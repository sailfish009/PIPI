package proteomics.Search;


import proteomics.Types.Peptide;
import proteomics.Types.SpectrumEntry;

import java.util.*;

public class CalSubscores {

    public CalSubscores(Peptide peptide, SpectrumEntry spectrum, float ms2Tolerance) {
        TreeMap<Float, Float> expPl = spectrum.plMap;
        float[][] ionMatrix = peptide.getIonMatrix();
        int precursorCharge = spectrum.precursorCharge;

        int matchedPeakNum = 0;
        Set<Integer> matchedIdxSet = new HashSet<>(); // 0 if the mz = 0 peak; 1 is the peak generated by b1 ion...
        matchedIdxSet.add(0);
        int maxRow = Math.min(ionMatrix.length, 2 * (precursorCharge - 1));
        if (precursorCharge == 1) {
            maxRow = 2;
        }
        int totalIonNum = ionMatrix[0].length * maxRow;
        Float[] intensityArray = expPl.values().toArray(new Float[expPl.size()]);
        Arrays.sort(intensityArray, Collections.reverseOrder());
        float intensityT = 0;
        if (totalIonNum < intensityArray.length) {
            intensityT = intensityArray[totalIonNum];
        }
        int matchedHighestPeakNum = 0;
        for (int i = 0; i < maxRow; ++i) {
            for (int j = 0; j < ionMatrix[0].length; ++j) {
                for (float mz : expPl.keySet()) {
                    if (Math.abs(mz - ionMatrix[i][j]) <= ms2Tolerance) {
                        if (expPl.get(mz) > intensityT) {
                            ++matchedHighestPeakNum;
                        }
                        ++matchedPeakNum;
                        if (i % 2 == 0) {
                            matchedIdxSet.add(j + 1);
                        } else {
                            if (j > 0) {
                                matchedIdxSet.add(j);
                            } else {
                                matchedIdxSet.add(ionMatrix[0].length);
                            }
                        }
                        break;
                    }
                }
            }
        }

        // calculate ion frac
        peptide.setIonFrac((double) matchedPeakNum / (double) totalIonNum);

        // calculate matched highest intensity frac
        if (matchedPeakNum > 0) {
            peptide.setMatchedHighestIntensityFrac((double) matchedHighestPeakNum / (double) matchedPeakNum);
        } else {
            peptide.setMatchedHighestIntensityFrac(0);
        }

        // calculate explained AA num
        Integer[] matchedIdxArray = matchedIdxSet.toArray(new Integer[matchedIdxSet.size()]);
        Arrays.sort(matchedIdxArray);
        int explainedAaNum = 0;
        if (matchedIdxArray.length > 1) {
            for (int i = 0; i < matchedIdxArray.length - 1; ++i) {
                if (matchedIdxArray[i + 1] - matchedIdxArray[i] == 1) {
                    ++explainedAaNum;
                }
            }
        }
        peptide.setExplainedAaFrac((double) explainedAaNum / (double) ionMatrix[0].length);
    }
}
