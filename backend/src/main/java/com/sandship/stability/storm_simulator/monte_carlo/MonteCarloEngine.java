package com.sandship.stability.storm_simulator.monte_carlo;

import com.sandship.stability.storm_simulator.wave_spectrum.WaveSpectrum;
import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class MonteCarloEngine {

    private static final double DEFAULT_ROLL_RADIUS_COEFFICIENT = 0.35;
    private static final double CAPSIZING_ROLL_THRESHOLD = 40.0;
    private static final double CAPSIZING_GM_THRESHOLD = 0.05;
    private static final int DEFAULT_TIME_STEP_MINUTES = 1;

    public static SimulationResult runSimulation(ShipParams shipParams, StormParams stormParams, int iterations) {
        double rollPeriod = calculateRollPeriod(shipParams);
        double rollStdDev = stormParams.waveHeight * 1.8;

        double periodRatio = stormParams.wavePeriod / rollPeriod;
        boolean parametricRollRisk = periodRatio > 0.8 && periodRatio < 1.2;
        double parametricAmplification = parametricRollRisk ? 1.8 : 1.0;

        double windPressure = 0.5 * WaveSpectrum.AIR_DENSITY * stormParams.windSpeed * stormParams.windSpeed;
        double windLateralArea = shipParams.lengthOverall * shipParams.bowHeight * 0.6;
        double windHeelArm = (windPressure * windLateralArea * shipParams.bowHeight * 0.5)
                / (shipParams.displacement * WaveSpectrum.SEAWATER_DENSITY * WaveSpectrum.GRAVITY);

        int capsizingCount = 0;
        double maxRollAngle = 0.0;
        double minGM = shipParams.baseGM;

        Random random = new Random();

        for (int i = 0; i < iterations; i++) {
            double rollAngle = random.nextGaussian() * rollStdDev * parametricAmplification;

            double windHeelAngle = windHeelArm / Math.max(shipParams.baseGM, 0.1);
            rollAngle += windHeelAngle;

            double rollAngleDeg = FastMath.abs(rollAngle);
            double dynamicGM = calculateDynamicGM(shipParams.baseGM, rollAngleDeg);

            if (FastMath.abs(rollAngle) > maxRollAngle) {
                maxRollAngle = FastMath.abs(rollAngle);
            }
            if (dynamicGM < minGM) {
                minGM = dynamicGM;
            }

            if (FastMath.abs(rollAngle) > CAPSIZING_ROLL_THRESHOLD && dynamicGM < CAPSIZING_GM_THRESHOLD) {
                capsizingCount++;
            }
        }

        double capsizingProbability = (double) capsizingCount / iterations;

        double rightingArmLoss = calculateRightingArmLoss(stormParams.waveHeight, parametricRollRisk);
        double weatherHelmEffect = calculateWeatherHelmEffect(stormParams.windSpeed, shipParams);
        double broachingProbability = calculateBroachingProbability(stormParams.waveHeight, stormParams.wavePeriod, rollPeriod);

        return new SimulationResult(
                capsizingProbability,
                capsizingCount,
                iterations,
                maxRollAngle,
                minGM,
                rollPeriod,
                periodRatio,
                parametricRollRisk,
                parametricAmplification,
                rightingArmLoss,
                weatherHelmEffect,
                broachingProbability,
                rollStdDev,
                windHeelArm
        );
    }

    public static double calculateDynamicGM(double baseGM, double rollAngleDeg) {
        double dynamicGM;
        if (rollAngleDeg < 15.0) {
            dynamicGM = baseGM * (1.0 - rollAngleDeg / 90.0 * 0.15);
        } else if (rollAngleDeg < 30.0) {
            double factor = (rollAngleDeg - 15.0) / 15.0;
            dynamicGM = baseGM * (0.975 - factor * 0.25);
        } else if (rollAngleDeg < 45.0) {
            double factor = (rollAngleDeg - 30.0) / 15.0;
            dynamicGM = baseGM * (0.725 - factor * 0.35);
        } else {
            double factor = Math.min((rollAngleDeg - 45.0) / 45.0, 1.0);
            dynamicGM = baseGM * (0.375 - factor * 0.5);
        }
        return Math.max(dynamicGM, -0.5);
    }

    public static double calculateRollPeriod(ShipParams shipParams) {
        if (shipParams.baseGM <= 0) return 0.0;

        double breadth = shipParams.breadthMolded;
        double kFactor = shipParams.rollRadiusCoefficient > 0
                ? shipParams.rollRadiusCoefficient
                : DEFAULT_ROLL_RADIUS_COEFFICIENT;
        double massRadiusOfGyration = breadth * kFactor;

        return 2 * Math.PI * massRadiusOfGyration /
                FastMath.sqrt(WaveSpectrum.GRAVITY * shipParams.baseGM);
    }

    public static boolean detectParametricRollRisk(double wavePeriod, double rollPeriod) {
        double periodRatio = wavePeriod / rollPeriod;
        return periodRatio > 0.8 && periodRatio < 1.2;
    }

    public static List<Map<String, Object>> generateRollAngleTimeSeries(
            int durationMinutes, double rollStdDev, double parametricAmplification,
            double windSpeed, ShipParams shipParams, double baseGM) {

        List<Map<String, Object>> timeSeries = new ArrayList<>();
        Random random = new Random();

        double currentRoll = 0.0;
        double windPressure = 0.5 * WaveSpectrum.AIR_DENSITY * windSpeed * windSpeed;
        double windLateralArea = shipParams.lengthOverall * shipParams.bowHeight * 0.6;
        double windHeelArm = (windPressure * windLateralArea * shipParams.bowHeight * 0.5)
                / (shipParams.displacement * WaveSpectrum.SEAWATER_DENSITY * WaveSpectrum.GRAVITY);
        double windHeelAngle = windHeelArm / Math.max(baseGM, 0.1);

        for (int minute = 0; minute < durationMinutes; minute += DEFAULT_TIME_STEP_MINUTES) {
            double randomComponent = random.nextGaussian() * rollStdDev * parametricAmplification * 0.3;
            double waveComponent = FastMath.sin(minute * 2 * Math.PI / 10.0) * rollStdDev * 0.5;

            currentRoll = currentRoll * 0.95 + randomComponent + waveComponent + windHeelAngle * 0.1;
            currentRoll = Math.max(-60.0, Math.min(60.0, currentRoll));

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("time", minute);
            point.put("rollAngle", round(currentRoll, 3));
            timeSeries.add(point);
        }

        return timeSeries;
    }

    public static List<Map<String, Object>> generateGMTimeSeries(
            int durationMinutes, double baseGM, List<Map<String, Object>> rollAngleSeries) {

        List<Map<String, Object>> timeSeries = new ArrayList<>();

        for (int i = 0; i < rollAngleSeries.size() && i * DEFAULT_TIME_STEP_MINUTES < durationMinutes; i++) {
            Map<String, Object> rollPoint = rollAngleSeries.get(i);
            double rollAngle = ((Number) rollPoint.get("rollAngle")).doubleValue();
            double rollAngleDeg = FastMath.abs(rollAngle);

            double dynamicGM = calculateDynamicGM(baseGM, rollAngleDeg);
            dynamicGM = Math.max(dynamicGM, -0.3);

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("time", i * DEFAULT_TIME_STEP_MINUTES);
            point.put("gm", round(dynamicGM, 4));
            timeSeries.add(point);
        }

        return timeSeries;
    }

    private static double calculateRightingArmLoss(double waveHeight, boolean parametricRollRisk) {
        double baseLoss = waveHeight * 2.0;
        if (parametricRollRisk) {
            baseLoss *= 1.5;
        }
        return Math.min(baseLoss, 80.0);
    }

    private static double calculateWeatherHelmEffect(double windSpeed, ShipParams shipParams) {
        double breadth = shipParams.breadthMolded;
        double length = shipParams.lengthOverall;
        double windPressure = 0.5 * WaveSpectrum.AIR_DENSITY * windSpeed * windSpeed;
        double lateralArea = length * shipParams.bowHeight * 0.6;
        return (windPressure * lateralArea * 0.5 * breadth)
                / (shipParams.displacement * WaveSpectrum.SEAWATER_DENSITY * WaveSpectrum.GRAVITY * length * 0.1);
    }

    private static double calculateBroachingProbability(double waveHeight, double wavePeriod, double rollPeriod) {
        if (waveHeight < 3.0) return 0.0;

        double periodMatch = 1.0 - FastMath.abs(wavePeriod - rollPeriod) / FastMath.max(wavePeriod, rollPeriod);
        double heightFactor = (waveHeight - 3.0) / 10.0;

        return Math.min(periodMatch * heightFactor * 0.5, 0.8);
    }

    private static double round(double value, int places) {
        long factor = (long) Math.pow(10, places);
        return (double) Math.round(value * factor) / factor;
    }

    public static class ShipParams {
        public final double lengthOverall;
        public final double breadthMolded;
        public final double displacement;
        public final double baseGM;
        public final double bowHeight;
        public final double rollRadiusCoefficient;

        public ShipParams(double lengthOverall, double breadthMolded, double displacement,
                          double baseGM, double bowHeight, double rollRadiusCoefficient) {
            this.lengthOverall = lengthOverall;
            this.breadthMolded = breadthMolded;
            this.displacement = displacement;
            this.baseGM = baseGM;
            this.bowHeight = bowHeight;
            this.rollRadiusCoefficient = rollRadiusCoefficient;
        }
    }

    public static class StormParams {
        public final double waveHeight;
        public final double wavePeriod;
        public final double windSpeed;
        public final String stormSeverity;

        public StormParams(double waveHeight, double wavePeriod, double windSpeed, String stormSeverity) {
            this.waveHeight = waveHeight;
            this.wavePeriod = wavePeriod;
            this.windSpeed = windSpeed;
            this.stormSeverity = stormSeverity;
        }
    }

    public static class SimulationResult {
        public final double capsizingProbability;
        public final int capsizingCount;
        public final int totalIterations;
        public final double maxRollAngle;
        public final double minGM;
        public final double rollPeriod;
        public final double periodRatio;
        public final boolean parametricRollRisk;
        public final double parametricAmplification;
        public final double rightingArmLossPercentage;
        public final double weatherHelmEffect;
        public final double broachingProbability;
        public final double rollStdDev;
        public final double windHeelArm;

        public SimulationResult(double capsizingProbability, int capsizingCount, int totalIterations,
                                double maxRollAngle, double minGM, double rollPeriod, double periodRatio,
                                boolean parametricRollRisk, double parametricAmplification,
                                double rightingArmLossPercentage, double weatherHelmEffect,
                                double broachingProbability, double rollStdDev, double windHeelArm) {
            this.capsizingProbability = capsizingProbability;
            this.capsizingCount = capsizingCount;
            this.totalIterations = totalIterations;
            this.maxRollAngle = maxRollAngle;
            this.minGM = minGM;
            this.rollPeriod = rollPeriod;
            this.periodRatio = periodRatio;
            this.parametricRollRisk = parametricRollRisk;
            this.parametricAmplification = parametricAmplification;
            this.rightingArmLossPercentage = rightingArmLossPercentage;
            this.weatherHelmEffect = weatherHelmEffect;
            this.broachingProbability = broachingProbability;
            this.rollStdDev = rollStdDev;
            this.windHeelArm = windHeelArm;
        }
    }
}
