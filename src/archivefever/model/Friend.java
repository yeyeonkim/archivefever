package archivefever.model;

public record Friend(String id, String name, String handle, Status status) {
    public enum Status {NONE, REQUESTED, INCOMING, ADDED}

    public boolean added() {return status == Status.ADDED;}

    public Friend withStatus(Status next) {return new Friend(id, name, handle, next);}
}
