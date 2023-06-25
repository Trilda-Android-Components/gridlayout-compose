/*
 * Copyright 2023 Jaewoong Cheon.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.woong.compose.grid

import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.max
import kotlin.math.min

internal class GridMeasureResult(
    val mainAxisCount: Int,
    val crossAxisCount: Int,
    val mainAxisSize: Int,
    val crossAxisSize: Int,
    val mainAxisPositions: IntArray,
    val crossAxisPositions: IntArray,
)

/**
 * A class to help grid layout measuring and placing.
 */
internal class GridMeasureHelper(
    val orientation: LayoutOrientation,
    val measurables: List<Measurable>,
    val placeables: Array<Placeable?>,
    val crossAxisCount: Int,
    val mainAxisArrangement: (Int, IntArray, LayoutDirection, Density, IntArray) -> Unit,
    val mainAxisSpacing: Dp,
    val crossAxisArrangement: (Int, IntArray, LayoutDirection, Density, IntArray) -> Unit,
    val crossAxisSpacing: Dp,
) {
    fun measure(
        measureScope: MeasureScope,
        constraints: Constraints,
    ): GridMeasureResult = with(measureScope) {
        @Suppress("NAME_SHADOWING")
        val constraints = OrientationIndependentConstraints(orientation, constraints)
        val mainAxisSpacingPx = mainAxisSpacing.roundToPx()
        val crossAxisSpacingPx = crossAxisSpacing.roundToPx()

        val measurableCount = measurables.size
        val mainAxisCount = measurableCount / crossAxisCount

        // Measure grid layout size and children's constraints.
        var i = 0
        var mainAxisPlacedSpace = 0
        var mainAxisSpaceAfterLast: Int
        var mainAxisTotalSize = 0
        var crossAxisTotalSize = 0
        for (m in 0 until mainAxisCount) {
            val mainAxisMax = constraints.mainAxisMaxSize
            var placeableMainAxisSizeMax = 0
            var crossAxisPlacedSpace = 0
            var crossAxisSpaceAfterLast: Int
            for (c in 0 until crossAxisCount) {
                val measurable = measurables[i]
                val crossAxisMax = constraints.crossAxisMaxSize
                val placeable = measurable.measure(
                    constraints = OrientationIndependentConstraints(
                        mainAxisMinSize = 0,
                        mainAxisMaxSize = if (mainAxisMax == Constraints.Infinity) {
                            Constraints.Infinity
                        } else {
                            mainAxisMax - mainAxisPlacedSpace
                        },
                        crossAxisMinSize = 0,
                        crossAxisMaxSize = if (crossAxisMax == Constraints.Infinity) {
                            Constraints.Infinity
                        } else {
                            crossAxisMax - crossAxisPlacedSpace
                        }
                    ).toConstraints(orientation)
                )
                crossAxisSpaceAfterLast = min(
                    crossAxisSpacingPx,
                    crossAxisMax - crossAxisPlacedSpace - placeable.crossAxisSize()
                )
                crossAxisPlacedSpace += placeable.crossAxisSize() + crossAxisSpaceAfterLast
                placeableMainAxisSizeMax = max(placeableMainAxisSizeMax, placeable.mainAxisSize())
                crossAxisTotalSize = max(crossAxisTotalSize, crossAxisPlacedSpace)
                placeables[i] = placeable
                i++
            }
            mainAxisSpaceAfterLast = min(
                mainAxisSpacingPx,
                mainAxisMax - mainAxisPlacedSpace - placeableMainAxisSizeMax
            )
            mainAxisPlacedSpace += placeableMainAxisSizeMax + mainAxisSpaceAfterLast
            mainAxisTotalSize = max(mainAxisTotalSize, mainAxisPlacedSpace)
        }
        val mainAxisLayoutSize = max(mainAxisTotalSize, constraints.mainAxisMinSize)
        val crossAxisLayoutSize = max(crossAxisTotalSize, constraints.crossAxisMinSize)

        // Measure children composable x, y positions.
        val mainAxisPositions = IntArray(mainAxisCount) { 0 }
        val mainAxisChildrenSizes = IntArray(mainAxisCount) { index ->
            // Placeable must not null on this time
            placeables[index]!!.mainAxisSize()
        }
        mainAxisArrangement(
            mainAxisLayoutSize,
            mainAxisChildrenSizes,
            this.layoutDirection,
            this,
            mainAxisPositions,
        )
        val crossAxisPositions = IntArray(crossAxisCount) { 0 }
        val crossAxisChildrenSizes = IntArray(crossAxisCount) { index ->
            // Placeable must not null on this time.
            placeables[index]!!.crossAxisSize()
        }
        crossAxisArrangement(
            crossAxisLayoutSize,
            crossAxisChildrenSizes,
            this.layoutDirection,
            this,
            crossAxisPositions,
        )

        GridMeasureResult(
            mainAxisSize = mainAxisLayoutSize,
            crossAxisSize = crossAxisLayoutSize,
            mainAxisCount = mainAxisCount,
            crossAxisCount = crossAxisCount,
            mainAxisPositions = mainAxisPositions,
            crossAxisPositions = crossAxisPositions,
        )
    }

    fun place(
        placeableScope: Placeable.PlacementScope,
        measureResult: GridMeasureResult,
    ) = with(placeableScope) {
        var i = 0
        for (m in 0 until measureResult.mainAxisCount) {
            for (c in 0 until measureResult.crossAxisCount) {
                val placeable = placeables[i]
                // Placeable must not null on this time.
                placeable!!

                if (orientation == LayoutOrientation.Horizontal) {
                    placeable.place(
                        x = measureResult.mainAxisPositions[m],
                        y = measureResult.crossAxisPositions[c],
                    )
                } else {
                    placeable.place(
                        x = measureResult.crossAxisPositions[c],
                        y = measureResult.mainAxisPositions[m],
                    )
                }
                i++
            }
        }
    }

    private fun Placeable.mainAxisSize(): Int {
        return if (orientation == LayoutOrientation.Horizontal) {
            width
        } else {
            height
        }
    }

    private fun Placeable.crossAxisSize(): Int {
        return if (orientation == LayoutOrientation.Horizontal) {
            height
        } else {
            width
        }
    }
}