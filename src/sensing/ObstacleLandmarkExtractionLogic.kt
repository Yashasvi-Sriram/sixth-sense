package sensing

import org.ejml.data.FMatrix2

interface ObstacleLandmarkExtractionLogic {
    fun getObservedObstaclesAndLandmarks(inputPoints: List<FMatrix2>, distances: List<Float>): Pair<List<Pair<FMatrix2, FMatrix2>>, List<FMatrix2>>
}