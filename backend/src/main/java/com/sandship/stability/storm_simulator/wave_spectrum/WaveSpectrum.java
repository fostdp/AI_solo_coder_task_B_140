package com.sandship.stability.storm_simulator.wave_spectrum;

import java.util.Map;

public class WaveSpectrum {

    public static final double GRAVITY = 9.81;
    public static final double AIR_DENSITY = 1.225;
    public static final double SEAWATER_DENSITY = 1.025;

    public static final Map<String, double[]> STORM_SEVERITY_PARAMS = Map.of(
            "TROPICAL_STORM", new double[]{3.0, 5.0, 17.0, 25.0, 7.0, 12.0},
            "SEVERE_STORM",   new double[]{5.0, 8.0, 25.0, 40.0, 9.0, 14.0},
            "TYPHOON",        new double[]{8.0, 14.0, 40.0, 60.0, 10.0, 16.0},
            "HURRICANE",      new double[]{14.0, 20.0, 60.0, 80.0, 12.0, 18.0}
    );

    private static final Map<Integer, SeaState> BEAUFORT_SEA_STATE_MAP = Map.ofEntries(
            Map.entry(1, new SeaState(1, "Calm (rippled)", 0.0, 0.1, 0.1, 1.0)),
            Map.entry(2, new SeaState(2, "Calm (glass smooth)", 0.1, 0.5, 0.5, 2.0)),
            Map.entry(3, new SeaState(3, "Smooth wavelets", 0.5, 1.25, 0.8, 3.0)),
            Map.entry(4, new SeaState(4, "Slight waves", 1.25, 2.5, 1.5, 4.0)),
            Map.entry(5, new SeaState(5, "Moderate waves", 2.5, 4.0, 2.5, 6.0)),
            Map.entry(6, new SeaState(6, "Rough seas", 4.0, 6.0, 4.0, 8.0)),
            Map.entry(7, new SeaState(7, "Very rough seas", 6.0, 9.0, 5.5, 10.0)),
            Map.entry(8, new SeaState(8, "High waves", 9.0, 14.0, 7.5, 12.0)),
            Map.entry(9, new SeaState(9, "Very high waves", 14.0, 17.0, 10.0, 14.0)),
            Map.entry(10, new SeaState(10, "Storm conditions", 17.0, 20.0, 12.5, 16.0)),
            Map.entry(11, new SeaState(11, "Violent storm", 20.0, 25.0, 16.0, 18.0)),
            Map.entry(12, new SeaState(12, "Hurricane force", 25.0, Double.MAX_VALUE, 20.0, 20.0))
    );

    public static SeaState beaufortToSeaState(int beaufortScale) {
        if (beaufortScale < 1 || beaufortScale > 12) {
            throw new IllegalArgumentException("蒲福风级必须在 1-12 之间，当前值: " + beaufortScale);
        }
        return BEAUFORT_SEA_STATE_MAP.get(beaufortScale);
    }

    public static SeaState beaufortToSeaStateFromWindSpeed(double windSpeedMs) {
        if (windSpeedMs < 0.3) {
            return BEAUFORT_SEA_STATE_MAP.get(1);
        } else if (windSpeedMs < 1.6) {
            return BEAUFORT_SEA_STATE_MAP.get(2);
        } else if (windSpeedMs < 3.4) {
            return BEAUFORT_SEA_STATE_MAP.get(3);
        } else if (windSpeedMs < 5.5) {
            return BEAUFORT_SEA_STATE_MAP.get(4);
        } else if (windSpeedMs < 8.0) {
            return BEAUFORT_SEA_STATE_MAP.get(5);
        } else if (windSpeedMs < 10.8) {
            return BEAUFORT_SEA_STATE_MAP.get(6);
        } else if (windSpeedMs < 13.9) {
            return BEAUFORT_SEA_STATE_MAP.get(7);
        } else if (windSpeedMs < 17.2) {
            return BEAUFORT_SEA_STATE_MAP.get(8);
        } else if (windSpeedMs < 20.8) {
            return BEAUFORT_SEA_STATE_MAP.get(9);
        } else if (windSpeedMs < 24.5) {
            return BEAUFORT_SEA_STATE_MAP.get(10);
        } else if (windSpeedMs < 28.5) {
            return BEAUFORT_SEA_STATE_MAP.get(11);
        } else {
            return BEAUFORT_SEA_STATE_MAP.get(12);
        }
    }

    public static JonswapParams calculateJonswapSpectrum(double significantWaveHeight, double peakPeriod) {
        double gamma = 3.3;
        double sigma = 0.07;
        double omegaP = 2.0 * Math.PI / peakPeriod;
        double alpha = 0.2 * Math.pow(significantWaveHeight, 2) * Math.pow(omegaP, 4) / Math.pow(GRAVITY, 2);

        return new JonswapParams(
                alpha,
                gamma,
                sigma,
                omegaP,
                significantWaveHeight,
                peakPeriod
        );
    }

    public static PiersonMoskowitzParams calculatePiersonMoskowitzSpectrum(double significantWaveHeight) {
        double omega0 = Math.sqrt(GRAVITY / (1.056 * significantWaveHeight));
        double alpha = 0.78 * Math.pow(significantWaveHeight, 2) * Math.pow(omega0, 4) / Math.pow(GRAVITY, 2);
        double peakPeriod = 2.0 * Math.PI / omega0;

        return new PiersonMoskowitzParams(
                alpha,
                omega0,
                significantWaveHeight,
                peakPeriod
        );
    }

    public static double calculateSpectrumValue(double omega, JonswapParams params) {
        double sigma = omega <= params.omegaP ? 0.07 : 0.09;
        double r = Math.exp(-Math.pow(omega - params.omegaP, 2) / (2 * Math.pow(sigma, 2) * Math.pow(params.omegaP, 2)));
        return params.alpha * Math.pow(GRAVITY, 2) * Math.pow(omega, -5)
                * Math.exp(-1.25 * Math.pow(params.omegaP / omega, 4))
                * Math.pow(params.gamma, r);
    }

    public static double calculateSpectrumValue(double omega, PiersonMoskowitzParams params) {
        return params.alpha * Math.pow(GRAVITY, 2) * Math.pow(omega, -5)
                * Math.exp(-1.25 * Math.pow(params.omega0 / omega, 4));
    }

    public static class SeaState {
        public final int beaufortScale;
        public final String description;
        public final double minWaveHeight;
        public final double maxWaveHeight;
        public final double averageWaveHeight;
        public final double dominantWavePeriod;

        public SeaState(int beaufortScale, String description, double minWaveHeight,
                        double maxWaveHeight, double averageWaveHeight, double dominantWavePeriod) {
            this.beaufortScale = beaufortScale;
            this.description = description;
            this.minWaveHeight = minWaveHeight;
            this.maxWaveHeight = maxWaveHeight;
            this.averageWaveHeight = averageWaveHeight;
            this.dominantWavePeriod = dominantWavePeriod;
        }
    }

    public static class JonswapParams {
        public final double alpha;
        public final double gamma;
        public final double sigma;
        public final double omegaP;
        public final double significantWaveHeight;
        public final double peakPeriod;

        public JonswapParams(double alpha, double gamma, double sigma, double omegaP,
                             double significantWaveHeight, double peakPeriod) {
            this.alpha = alpha;
            this.gamma = gamma;
            this.sigma = sigma;
            this.omegaP = omegaP;
            this.significantWaveHeight = significantWaveHeight;
            this.peakPeriod = peakPeriod;
        }
    }

    public static class PiersonMoskowitzParams {
        public final double alpha;
        public final double omega0;
        public final double significantWaveHeight;
        public final double peakPeriod;

        public PiersonMoskowitzParams(double alpha, double omega0,
                                      double significantWaveHeight, double peakPeriod) {
            this.alpha = alpha;
            this.omega0 = omega0;
            this.significantWaveHeight = significantWaveHeight;
            this.peakPeriod = peakPeriod;
        }
    }
}
