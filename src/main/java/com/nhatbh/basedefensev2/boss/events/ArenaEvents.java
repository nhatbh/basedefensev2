package com.nhatbh.basedefensev2.boss.events;

import net.minecraftforge.eventbus.api.Event;

public class ArenaEvents {
    
    public static class StageStart extends Event {
        private final int stageNumber;
        public StageStart(int stageNumber) {
            this.stageNumber = stageNumber;
        }
        public int getStageNumber() {
            return stageNumber;
        }
    }

    public static class WaveStart extends Event {
        private final int waveNumber;
        public WaveStart(int waveNumber) {
            this.waveNumber = waveNumber;
        }
        public int getWaveNumber() {
            return waveNumber;
        }
    }

    public static class WaveComplete extends Event {
        private final int waveNumber;
        public WaveComplete(int waveNumber) {
            this.waveNumber = waveNumber;
        }
        public int getWaveNumber() {
            return waveNumber;
        }
    }
}
