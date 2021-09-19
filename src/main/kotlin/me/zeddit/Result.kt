package me.zeddit

// result from rust for good async error handling
open class Result<T, E> private constructor(val ok: T?, val err: E?) {

    constructor(ok: T) : this(ok = ok, err = null)

    constructor(err: E) : this(ok = null, err = err)


}