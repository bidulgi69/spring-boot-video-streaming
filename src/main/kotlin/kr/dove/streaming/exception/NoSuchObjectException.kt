package kr.dove.streaming.exception

class NoSuchObjectException: Throwable {
    constructor(cause: Throwable): super(cause)
    constructor(message: String): super(message)
    constructor(message: String, cause: Throwable): super(message, cause)
}