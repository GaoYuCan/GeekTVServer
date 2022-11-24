package top.sinkdev.model

data class SimpleResponse<T>(val code: Int, val msg: String, val data: T?)

fun <T> createSuccessResponse(data: T, msg: String = "OK"): SimpleResponse<T> {
    return SimpleResponse<T>(0, msg, data)
}

fun <T> createFailureResponse(msg: String, code: Int = -1): SimpleResponse<T> {
    return SimpleResponse<T>(code, msg, null)
}