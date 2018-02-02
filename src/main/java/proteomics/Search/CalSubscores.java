package proteomics.Search;


import proteomics.Spectrum.PreSpectrum;
import proteomics.Types.Coordinate;
import proteomics.Types.Peptide;
import proteomics.Types.PositionDeltaMassMap;

import java.util.*;

public class CalSubscores {

    private static final double p = PreSpectrum.topN * 0.01;

    public CalSubscores(Peptide peptide, float ms2Tolerance, TreeMap<Float, Float> expPl, int precursorCharge, TreeSet<Peptide> ptmPatterns, Binomial binomial) {
        float[][] ionMatrix = peptide.getIonMatrix();

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

        // calculate A score
        if (peptide.hasVarPTM()) {
            double finalAScore = -9999;
            for (int localTopN = 1; localTopN <= PreSpectrum.topN; ++localTopN) {
                TreeMap<Float, Float> localPlMap = PreSpectrum.selectTopN(expPl, localTopN);
                Set<String> totalAffectedPeakSet = new HashSet<>();
                Set<String> topMatchedPeakSet = new HashSet<>();
                Set<String> secondMatchedPeakSet = new HashSet<>();
                sub(peptide.getVarPTMs(), ionMatrix, localPlMap, ms2Tolerance, totalAffectedPeakSet, topMatchedPeakSet);
                Peptide[] tempArray = ptmPatterns.toArray(new Peptide[ptmPatterns.size()]);
                double aScore;
                if (tempArray.length > 1) {
                    sub(tempArray[1].getVarPTMs(), tempArray[1].getIonMatrix(), localPlMap, ms2Tolerance, totalAffectedPeakSet, secondMatchedPeakSet);
                    aScore = -10 * Math.log10(binomial.calPValue(totalAffectedPeakSet.size(), topMatchedPeakSet.size(), p)) + 10 * Math.log10(binomial.calPValue(totalAffectedPeakSet.size(), secondMatchedPeakSet.size(), p));
                } else {
                    aScore = -10 * Math.log10(binomial.calPValue(totalAffectedPeakSet.size(), topMatchedPeakSet.size(), p));
                }
                if (aScore > finalAScore) {
                    finalAScore = aScore;
                }
            }
            peptide.setaScore(String.valueOf(finalAScore));
        }
    }

    private void sub(PositionDeltaMassMap varPtmMap, float[][] ionMatrix, TreeMap<Float, Float> localPlMap, float ms2Tolerance, Set<String> totalAffectedPeakSet, Set<String> matchedIonTypeSet) {
        for (Coordinate co : varPtmMap.keySet()) {
            if (co.x == 0 || co.x == 1) {
                totalAffectedPeakSet.add("b1");
                totalAffectedPeakSet.add(String.format(Locale.US, "y%d", ionMatrix[0].length - 1));
                for (float mz : localPlMap.keySet()) {
                    if (Math.abs(mz - ionMatrix[0][0]) <= ms2Tolerance) {
                        matchedIonTypeSet.add("b1");
                    } else if (Math.abs(mz - ionMatrix[1][1]) <= ms2Tolerance) {
                        matchedIonTypeSet.add(String.format(Locale.US, "y%d", ionMatrix[0].length - 1));
                    }
                }
            } else if (co.x == ionMatrix[0].length + 1 || co.x == ionMatrix[0].length) {
                totalAffectedPeakSet.add(String.format(Locale.US, "b%d", ionMatrix[0].length - 1));
                totalAffectedPeakSet.add("y1");
                for (float mz : localPlMap.keySet()) {
                    if (Math.abs(mz - ionMatrix[0][ionMatrix[0].length - 2]) <= ms2Tolerance) {
                        matchedIonTypeSet.add(String.format(Locale.US, "b%d", ionMatrix[0].length - 1));
                    } else if (Math.abs(mz - ionMatrix[1][ionMatrix[0].length - 1]) <= ms2Tolerance) {
                        matchedIonTypeSet.add("y1");
                    }
                }
            } else {
                totalAffectedPeakSet.add(String.format(Locale.US, "b%d", co.x - 1));
                totalAffectedPeakSet.add(String.format(Locale.US, "b%d", co.x));
                totalAffectedPeakSet.add(String.format(Locale.US, "y%d", ionMatrix[0].length - co.x));
                totalAffectedPeakSet.add(String.format(Locale.US, "y%d", ionMatrix[0].length - co.x + 1));
                for (float mz : localPlMap.keySet()) {
                    if (Math.abs(mz - ionMatrix[0][co.x - 2]) <= ms2Tolerance) {
                        matchedIonTypeSet.add(String.format(Locale.US, "b%d", co.x - 1));
                    } else if (Math.abs(mz - ionMatrix[0][co.x - 1]) <= ms2Tolerance) {
                        matchedIonTypeSet.add(String.format(Locale.US, "b%d", co.x));
                    } else if (Math.abs(mz - ionMatrix[1][co.x - 1]) <= ms2Tolerance) {
                        matchedIonTypeSet.add(String.format(Locale.US, "y%d", ionMatrix[0].length - co.x + 1));
                    } else if (Math.abs(mz - ionMatrix[1][co.x]) <= ms2Tolerance) {
                        matchedIonTypeSet.add(String.format(Locale.US, "y%d", ionMatrix[0].length - co.x));
                    }
                }
            }
        }
    }
}
