package com.sandship.stability.vr_loading.strategy;

import com.sandship.stability.entity.CargoHold;
import com.sandship.stability.entity.Ship;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

public class StrategyEngine {

    public double calculateDefaultLoadWeight(double remainingCapacity, double available) {
        if (remainingCapacity <= 0) {
            return 0;
        }
        double defaultWeight = Math.max(5, remainingCapacity * 0.2);
        double upperBound = Math.min(available, remainingCapacity);
        return Math.min(defaultWeight, upperBound);
    }

    public double[] calculateCGChange(double currentCgX, double currentCgY, double currentCgZ,
                                      double currentWeight, double addWeight,
                                      double holdCgX, double holdCgY, double holdCgZ) {
        if (addWeight <= 0) {
            return new double[]{currentCgX, currentCgY, currentCgZ};
        }
        double totalWeight = currentWeight + addWeight;
        if (totalWeight <= 0) {
            return new double[]{holdCgX, holdCgY, holdCgZ};
        }
        double newCgX = (currentCgX * currentWeight + holdCgX * addWeight) / totalWeight;
        double newCgY = (currentCgY * currentWeight + holdCgY * addWeight) / totalWeight;
        double newCgZ = (currentCgZ * currentWeight + holdCgZ * addWeight) / totalWeight;
        return new double[]{
                roundDouble(newCgX, 3),
                roundDouble(newCgY, 3),
                roundDouble(newCgZ, 3)
        };
    }

    public Map<UUID, Double> recommendLoading(Ship ship, List<CargoHold> holds) {
        Map<UUID, Double> recommendation = new LinkedHashMap<>();
        if (holds == null || holds.isEmpty()) {
            return recommendation;
        }

        double deadweight = ship.getDeadweightTons() != null
                ? ship.getDeadweightTons().doubleValue() : 0;
        if (deadweight <= 0) {
            return recommendation;
        }

        List<CargoHold> sortedHolds = sortHoldsForLoading(holds);
        double totalCapacity = sortedHolds.stream()
                .mapToDouble(h -> h.getMaxWeight() != null ? h.getMaxWeight().doubleValue() : 0)
                .sum();

        if (totalCapacity <= 0) {
            return recommendation;
        }

        double loadTarget = Math.min(deadweight, totalCapacity) * 0.7;
        double shipLength = ship.getLengthOverall() != null
                ? ship.getLengthOverall().doubleValue() : 1;
        double midshipX = shipLength / 2.0;

        double remainingLoad = loadTarget;
        List<CargoHold> lowHolds = sortedHolds.stream()
                .filter(h -> h.getCenterGravityZ() != null
                        && h.getCenterGravityZ().doubleValue() < getAverageCgZ(sortedHolds))
                .collect(Collectors.toList());
        List<CargoHold> highHolds = sortedHolds.stream()
                .filter(h -> !lowHolds.contains(h))
                .collect(Collectors.toList());

        double heavyRatio = 0.6;
        double heavyAllocation = loadTarget * heavyRatio;
        double lightAllocation = loadTarget * (1 - heavyRatio);

        remainingLoad = distributeWeightEvenly(lowHolds, heavyAllocation, midshipX, recommendation);
        remainingLoad += distributeWeightEvenly(highHolds, lightAllocation, midshipX, recommendation);

        if (remainingLoad > 0) {
            remainingLoad = distributeWeightEvenly(sortedHolds, remainingLoad, midshipX, recommendation);
        }

        return recommendation;
    }

    private List<CargoHold> sortHoldsForLoading(List<CargoHold> holds) {
        return holds.stream()
                .filter(h -> !Boolean.TRUE.equals(h.getIsTank()))
                .sorted((h1, h2) -> {
                    int zCompare = compareBigDecimal(h1.getCenterGravityZ(), h2.getCenterGravityZ());
                    if (zCompare != 0) {
                        return zCompare;
                    }
                    int holdCompare = compareInteger(h1.getHoldNumber(), h2.getHoldNumber());
                    if (holdCompare != 0) {
                        return holdCompare;
                    }
                    return compareBigDecimal(h1.getMaxWeight(), h2.getMaxWeight());
                })
                .collect(Collectors.toList());
    }

    private double distributeWeightEvenly(List<CargoHold> holds, double totalWeight,
                                           double midshipX, Map<UUID, Double> recommendation) {
        if (holds == null || holds.isEmpty() || totalWeight <= 0) {
            return totalWeight;
        }

        holds.sort((h1, h2) -> {
            double x1 = h1.getCenterGravityX() != null ? h1.getCenterGravityX().doubleValue() : midshipX;
            double x2 = h2.getCenterGravityX() != null ? h2.getCenterGravityX().doubleValue() : midshipX;
            return Double.compare(Math.abs(x1 - midshipX), Math.abs(x2 - midshipX));
        });

        int holdCount = holds.size();
        double weightPerHold = totalWeight / holdCount;
        double remaining = totalWeight;

        for (int i = 0; i < holdCount; i++) {
            CargoHold hold = holds.get(i);
            UUID holdId = hold.getId();
            double currentAssigned = recommendation.getOrDefault(holdId, 0.0);
            double maxAllowed = hold.getMaxWeight() != null
                    ? hold.getMaxWeight().doubleValue() - currentAssigned : 0;
            if (maxAllowed <= 0) {
                continue;
            }

            double assignWeight = Math.min(weightPerHold, maxAllowed);
            if (i == holdCount - 1) {
                assignWeight = Math.min(remaining, maxAllowed);
            }
            if (assignWeight > 0) {
                recommendation.put(holdId, currentAssigned + roundDouble(assignWeight, 2));
                remaining -= assignWeight;
            }
        }

        return Math.max(0, roundDouble(remaining, 2));
    }

    private double getAverageCgZ(List<CargoHold> holds) {
        if (holds == null || holds.isEmpty()) {
            return 0;
        }
        double sum = 0;
        int count = 0;
        for (CargoHold hold : holds) {
            if (hold.getCenterGravityZ() != null) {
                sum += hold.getCenterGravityZ().doubleValue();
                count++;
            }
        }
        return count > 0 ? sum / count : 0;
    }

    private int compareBigDecimal(BigDecimal a, BigDecimal b) {
        double da = a != null ? a.doubleValue() : 0;
        double db = b != null ? b.doubleValue() : 0;
        return Double.compare(da, db);
    }

    private int compareInteger(Integer a, Integer b) {
        int ia = a != null ? a : 0;
        int ib = b != null ? b : 0;
        return Integer.compare(ia, ib);
    }

    private double roundDouble(double value, int places) {
        BigDecimal bd = BigDecimal.valueOf(value);
        return bd.setScale(places, RoundingMode.HALF_UP).doubleValue();
    }
}
