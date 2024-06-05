package wonder.shaderdisplay.entry;

public class BadInitException extends Exception {

    public BadInitException(String message) {
        super(message);
    }

    public BadInitException(String message, Throwable cause) {
        super(message, cause);
    }

    public BadInitException(Throwable cause) {
        super(cause);
    }

    protected BadInitException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
