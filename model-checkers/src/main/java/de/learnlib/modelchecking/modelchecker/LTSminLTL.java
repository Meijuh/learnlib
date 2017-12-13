/* Copyright (C) 2013-2017 TU Dortmund
 * This file is part of LearnLib, http://www.learnlib.de/.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.learnlib.modelchecking.modelchecker;

import de.learnlib.api.modelchecking.counterexample.Lasso;
import de.learnlib.api.modelchecking.modelchecker.ModelChecker;
import de.learnlib.api.modelchecking.modelchecker.ModelCheckingException;
import lombok.AccessLevel;
import lombok.Getter;
import net.automatalib.automata.concepts.Output;
import net.automatalib.parser.FSMParseException;
import net.automatalib.ts.simple.SimpleDTS;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * An LTL model checker using LTSmin.
 *
 * Key in this implementation is that the LTSmin binaries are extracted from the JAR file and written to a temporary
 * file (which is deleted when the JVM exits).
 *
 * This model checker is implemented as follows. The hypothesis automaton is first written to an LTS in ETF
 * {@link net.automatalib.serialization.etf.writer.ETFWriter} file, which serves as input for the etf2lts-mc binary.
 * Then the etf2lts-mc binary is run, which will write an LTS in GCF format. This LTS will be a subset of the
 * language of the given hypothesis. Next, the GCF is converted to FSM using the ltsmin-convert binary. Lastly, the
 * FSM is read back into an automaton using an {@link net.automatalib.parser.FSMParser}.
 *
 * @author Jeroen Meijer
 *
 * @see http://ltsmin.utwente.nl
 * @see net.automatalib.parser.FSMParser
 * @see net.automatalib.serialization.etf.writer.ETFWriter
 *
 * @param <I> the input type.
 * @param <A> the output type.
 * @param <L> the Lasso type.
 */
public abstract class LTSminLTL<I,
                                A extends SimpleDTS<?, I> & Output<I, ?>,
                                L extends Lasso<?, ? extends A, I, ?>>
        extends UnfoldingModelChecker<I, A, String, L> implements ModelChecker<I, A, String, L> {

    /**
     * Returns the operating system name; on this path the binary can be found.
     *
     * @return the OS name.
     */
    private static String getOSName() {
        String osName = System.getProperty("os.name");
        if (osName.contains("Mac")) osName = "Mac OS X";
        else if (osName.contains("Linux")) osName = "Linux";
        else if (osName.contains("Windows")) osName = "Windows 10";
        return osName;
    }

    /**
     * Returns the extension binaries have on the OS (e.g. ".exe" on Windows).
     *
     * @return the extension
     */
    private static String getExeExt() {
        String exeExt = "";
        if (getOSName().contains("Windows")) exeExt = ".exe";
        return exeExt;
    }

    /**
     * The path to the etf2lts-mc binary.
     */
    private static String etf2ltsMc = null;

    /**
     * The path to the ltsmin-convert binary.
     */
    private static String ltsminConvert = null;

    /**
     * Extract the etf2lts-mc binary from the JAR file and write it to a temporary file.
     *
     * After calling this method {@link #etf2ltsMc} will be set.
     *
     * @throws IOException when the binary could not be found.
     */
    private static void makeEtf2ltsMc() throws IOException {
        if (etf2ltsMc == null) {
            final InputStream is = LTSminLTL.class.getClass().getResourceAsStream(
                    "/" + getOSName() + "/etf2lts-mc" + getExeExt());
            if (is != null) {
                final File etf2ltsMC = File.createTempFile("etf2lts-mc", getExeExt());
                Files.copy(is, etf2ltsMC.toPath(), StandardCopyOption.REPLACE_EXISTING);
                etf2ltsMC.deleteOnExit();
                if (!etf2ltsMC.setExecutable(true)) throw new IOException("unable to set executable bit");
                LTSminLTL.etf2ltsMc = etf2ltsMC.getAbsolutePath();
            } else LTSminLTL.etf2ltsMc = "etf2lts-mc";
        }
    }

    /**
     * Extract the ltsmin-convert binary from the JAR file and write it to a temporary file.
     *
     * After calling this method {@link #ltsminConvert} will be set.
     *
     * @throws IOException
     */
    private static void makeLtsminConvert() throws IOException {
        if (ltsminConvert == null) {
            final InputStream is = LTSminLTL.class.getClass().getResourceAsStream(
                    "/" + getOSName() + "/ltsmin-convert" + getExeExt());
            if (is != null) {
                final File ltsminConvert = File.createTempFile("ltsmin-convert", getExeExt());
                ltsminConvert.deleteOnExit();
                Files.copy(is, ltsminConvert.toPath(), StandardCopyOption.REPLACE_EXISTING);
                if (!ltsminConvert.setExecutable(true)) throw new IOException("unable to set executable bit");
                LTSminLTL.ltsminConvert = ltsminConvert.getAbsolutePath();
            } else LTSminLTL.ltsminConvert = "ltsmin-convert";
        }
    }

    /**
     * Whether intermediate files should be kept, e.g. etfs, gcfs, etc.
     */
    @Getter(AccessLevel.PROTECTED)
    private final boolean keepFiles;

    /**
     * The function that transforms edges in FSM files to actual input.
     */
    @Getter(AccessLevel.PUBLIC)
    private final Function<String, I> string2Input;

    /**
     * Whether all streams from standard-in, -out, and -error should be inherited.
     */
    private final boolean inheritIO;

    /**
     * Constructs a new LTSminLTL.
     *
     * @param keepFiles whether to keep intermediate files, (e.g. etfs, gcfs etc.).
     * @param string2Input a function that transforms edges in FSM files to actual input.
     * @param minimumUnfolds the minimum number of unfolds.
     * @param multiplier the multiplier
     * @param inheritIO whether to print output from LTSmin on stdout, and stderr.
     */
    protected LTSminLTL(boolean keepFiles,
                        Function<String, I> string2Input,
                        int minimumUnfolds,
                        double multiplier,
                        boolean inheritIO) {
        super(minimumUnfolds, multiplier);
        this.keepFiles = keepFiles;
        this.string2Input = string2Input;
        this.inheritIO = inheritIO;
    }

    /**
     * Writes the given {@code automaton} to the given {@code etf} file.
     *
     * @param automaton the automaton to write.
     * @param inputs the alphabet.
     * @param etf the file to write to.
     *
     * @throws IOException
     */
    protected abstract void automaton2ETF(A automaton, Collection<? extends I> inputs, File etf) throws IOException;

    /**
     * Reads the {@code fsm} and converts it to a {@link Lasso}.
     *
     * @param fsm the FSM to read.
     * @param automaton the automaton that was used as a hypothesis.
     *
     * @return the {@link Lasso}.
     *
     * @throws IOException
     * @throws FSMParseException
     */
    protected abstract L fsm2Lasso(File fsm, A automaton) throws IOException, FSMParseException;

    /**
     * Finds a counterexample for the given {@code formula}, and given {@code hypothesis}.
     *
     * @see LTSminLTL
     */
    @Override
    public final L findCounterExample(A hypothesis, Collection<? extends I> inputs, String formula) throws ModelCheckingException {

        final L result;

        final File etf, gcf;
        try {
            // create the ETF that will contain the LTS of the hypothesis
            etf = File.createTempFile("automaton2etf", ".etf");

            // create the GCF that will possibly contain the counterexample
            gcf = File.createTempFile("etf2gcf", ".gcf");

            // write to the ETF file
            automaton2ETF(hypothesis, inputs, etf);

            // extract the etf2lts-mc binary from the JAR file
            makeEtf2ltsMc();

            // extract the ltsmin-convert binary from the JAR file
            makeLtsminConvert();
        } catch (IOException ioe) {
            throw new ModelCheckingException(ioe);
        }

        // the command lines for the ProcessBuilder
        final List<String> commandLines = new ArrayList();

        // add the etf2lts-mc binary
        commandLines.add(etf2ltsMc);

        // add the ETF file that contains the LTS of the hypothesis
        commandLines.add(etf.getAbsolutePath());

        // add the LTL formula
        commandLines.add("--ltl=" + formula);

        // use Buchi automata created by spot
        commandLines.add("--buchi-type=spotba");

        // use the Union-Find strategy
        commandLines.add("--strategy=ufscc");

        // write the lasso to this file
        commandLines.add("--trace=" + gcf.getAbsolutePath());

        // use only one thread (hypotheses are always small)
        commandLines.add("--threads=1");

        // use LTSmin LTL semantics
        commandLines.add("--ltl-semantics=ltsmin");

        // do not abort on partial LTSs
        commandLines.add("--allow-undefined-edges");

        final Process ltsmin;
        try {
            // run the etf2lts-mc binary
            ProcessBuilder processBuilder = new ProcessBuilder(commandLines);
            if (inheritIO) processBuilder = processBuilder.inheritIO();
            ltsmin = processBuilder.start();
            ltsmin.waitFor();
        } catch (IOException | InterruptedException e) {
            throw new ModelCheckingException(e);
        }

        // check if we need to delete the ETF
        if (!keepFiles && !etf.delete()) {
            throw new ModelCheckingException("Could not delete file: " + etf.getAbsolutePath());
        }

        if (ltsmin.exitValue() == 1) {
            // we have found a counterexample
            commandLines.clear();

            final File fsm;
            try {
                // create a file for the FSM
                fsm = File.createTempFile("gcf2fsm", ".fsm");
            } catch (IOException ioe) {
                throw new ModelCheckingException(ioe);
            }

            // add the ltsmin-convert binary
            commandLines.add(ltsminConvert);

            // use the GCF as input
            commandLines.add(gcf.getAbsolutePath());

            // use the FSM as output
            commandLines.add(fsm.getAbsolutePath());

            // required option
            commandLines.add("--rdwr");

            final Process convert;
            try {
                // convert the GCF to FSM
                ProcessBuilder processBuilder = new ProcessBuilder(commandLines);
                if (inheritIO) processBuilder = processBuilder.inheritIO();
                convert = processBuilder.start();
                convert.waitFor();
            } catch (IOException | InterruptedException e) {
                throw new ModelCheckingException(e);
            }

            // check the conversion is successful
            if (convert.exitValue() != 0) throw new ModelCheckingException("Could not convert gcf to fsm");

            try {
                // convert the FSM to a Lasso
                result = fsm2Lasso(fsm, hypothesis);

                // check if we must keep the FSM file
                if (!keepFiles && !fsm.delete()) {
                    throw new ModelCheckingException("Could not delete file: " + fsm.getAbsolutePath());
                }
            } catch (IOException | FSMParseException e) {
                throw new ModelCheckingException(e);
            }
        } else result = null;

        // check if we must keep the GCF
        if (!keepFiles && !gcf.delete()) {
            throw new ModelCheckingException("Could not delete file: " + gcf.getAbsolutePath());
        }

        return result;
    }

    public static class BuilderDefaults {

        public static boolean keepFiles() {
            return false;
        }

        public static int minimumUnfolds() {
            return 3; // super arbitrary number
        }

        public static double multiplier() {
            return 1.0; // quite arbitrary too
        }

        public static boolean inheritIO() {
            return false;
        }
    }
}
