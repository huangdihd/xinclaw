/*
 *   Copyright (C) 2026 huangdihd
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package xin.agent.pathfinding;

import xin.bbtt.movement.Movement;

/**
 * 一个空移动类，用于在移动队列中产生延迟。
 */
public class IdleMovement extends Movement {
    private final long duration;

    public IdleMovement(long durationMs) {
        this.duration = durationMs;
    }

    @Override
    public void init() {
        // 不执行任何动作
    }

    @Override
    public void onTick() {
        // 不执行任何动作
    }

    @Override
    public long getTime() {
        return duration;
    }

    @Override
    public void onStop() {
        // 不执行任何动作
    }
}
