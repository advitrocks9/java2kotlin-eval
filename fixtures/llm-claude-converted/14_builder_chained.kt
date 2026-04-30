package edgecases.builder

class Sample {
    private var host: String = "localhost"
    private var port: Int = 80
    private var tls: Boolean = false

    fun host(host: String): Sample { this.host = host; return this }
    fun port(port: Int): Sample { this.port = port; return this }
    fun tls(tls: Boolean): Sample { this.tls = tls; return this }

    fun url(): String = (if (tls) "https" else "http") + "://" + host + ":" + port
}
