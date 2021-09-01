package com.example.objectdetection

import android.graphics.RectF

/**
 * @author mvn-vinhnguyen on 9/1/21.
 */
data class DetectionResult(val boundingBox: RectF, val text: String)
