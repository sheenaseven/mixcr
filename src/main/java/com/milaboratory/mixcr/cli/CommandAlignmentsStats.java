/*
 * Copyright (c) 2014-2019, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
 * (here and after addressed as Inventors)
 * All Rights Reserved
 *
 * Permission to use, copy, modify and distribute any part of this program for
 * educational, research and non-profit purposes, by non-profit institutions
 * only, without fee, and without a written agreement is hereby granted,
 * provided that the above copyright notice, this paragraph and the following
 * three paragraphs appear in all copies.
 *
 * Those desiring to incorporate this work into commercial products or use for
 * commercial purposes should contact MiLaboratory LLC, which owns exclusive
 * rights for distribution of this program for commercial purposes, using the
 * following email address: licensing@milaboratory.com.
 *
 * IN NO EVENT SHALL THE INVENTORS BE LIABLE TO ANY PARTY FOR DIRECT, INDIRECT,
 * SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST PROFITS,
 * ARISING OUT OF THE USE OF THIS SOFTWARE, EVEN IF THE INVENTORS HAS BEEN
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * THE SOFTWARE PROVIDED HEREIN IS ON AN "AS IS" BASIS, AND THE INVENTORS HAS
 * NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR
 * MODIFICATIONS. THE INVENTORS MAKES NO REPRESENTATIONS AND EXTENDS NO
 * WARRANTIES OF ANY KIND, EITHER IMPLIED OR EXPRESS, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY OR FITNESS FOR A
 * PARTICULAR PURPOSE, OR THAT THE USE OF THE SOFTWARE WILL NOT INFRINGE ANY
 * PATENT, TRADEMARK OR OTHER RIGHTS.
 */
package com.milaboratory.mixcr.cli;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.VoidProcessor;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader;
import com.milaboratory.mixcr.info.AlignmentInfoCollector;
import com.milaboratory.mixcr.info.GeneFeatureCoverageCollector;
import com.milaboratory.mixcr.info.ReferencePointCoverageCollector;
import com.milaboratory.util.SmartProgressReporter;
import io.repseq.core.GeneFeature;
import io.repseq.core.ReferencePoint;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Collections;
import java.util.List;

import static io.repseq.core.GeneFeature.*;
import static io.repseq.core.ReferencePoint.*;

@Command(name = "alignmentsStat",
        sortOptions = true,
        hidden = true,
        separator = " ",
        description = "Alignments statistics.")
public class CommandAlignmentsStats extends ACommandMiXCR {
    @Parameters(index = "0", description = "input_file.vdjca")
    public String in;
    @Parameters(index = "1", description = "[output.txt]", arity = "0..1")
    public String out = null;

    @Override
    protected List<String> getInputFiles() {
        return Collections.singletonList(in);
    }

    @Override
    protected List<String> getOutputFiles() {
        return out == null ? Collections.emptyList() : Collections.singletonList(out);
    }


    @Override
    public void run0() throws Exception {
        long[] geneFeatureCounters = new long[targetFeatures.length];
        AlignmentInfoCollector[] collectors = new AlignmentInfoCollector[targetFeatures.length + targetReferencePoints.length];

        int i = 0;

        for (GeneFeature targetFeature : targetFeatures)
            collectors[i++] = new GeneFeatureCoverageCollector(targetFeature);

        for (ReferencePoint targetReferencePoint : targetReferencePoints)
            collectors[i++] = new ReferencePointCoverageCollector(targetReferencePoint, 40, 40);

        final Collector collector = new Collector(collectors);

        try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(in);
             PrintStream output = out == null ? System.out :
                     new PrintStream(new BufferedOutputStream(new FileOutputStream(out), 32768))
        ) {
            SmartProgressReporter.startProgressReport("Analysis", reader);
            CUtils.processAllInParallel(reader, collector, Math.min(4, Runtime.getRuntime().availableProcessors()));
            collector.end();

            if (output == System.out)
                output.println();

            collector.write(output);
        }
    }

    private static final GeneFeature[] targetFeatures = {
            V5UTR,
            new GeneFeature(L1Begin, -20, 0),
            L1, VIntron, L2, FR1, CDR1, FR2, CDR2, FR3, CDR3, FR4,
            new GeneFeature(FR4, 0, -3)
    };

    private static final ReferencePoint[] targetReferencePoints = {
            L1Begin, L1End, L2Begin, FR1Begin, CDR1Begin, FR2Begin, CDR2Begin, FR3Begin, CDR3Begin,
            FR4Begin, FR4End
    };

    private static class Collector implements VoidProcessor<VDJCAlignments> {
        final AlignmentInfoCollector[] collectors;

        public Collector(AlignmentInfoCollector... collectors) {
            this.collectors = collectors;
        }

        @Override
        public void process(VDJCAlignments input) {
            for (AlignmentInfoCollector collector : collectors)
                collector.put(input);
        }

        public void end() {
            for (AlignmentInfoCollector collector : collectors)
                collector.end();
        }

        public void write(PrintStream writer) {
            for (AlignmentInfoCollector collector : collectors)
                collector.writeResult(writer);
        }
    }
}
