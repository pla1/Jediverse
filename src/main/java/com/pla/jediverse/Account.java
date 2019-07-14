package com.pla.jediverse;

class Account implements Comparable<Account> {
    private String displayName;
    private String accountName;
    private String url;
    private String id;

    public Account(String id, String accountName, String displayName, String url) {
        this.id = id;
        this.accountName = accountName;
        this.displayName = displayName;
        this.url = url;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String toString() {
        return String.format("%s %s %s %s", id, accountName, displayName, url);
    }

    public String getDisplayNameAndAccount() {
        if (Utils.isBlank(displayName)) {
            return String.format("<%s>", accountName);
        } else {
           return String.format("%s <%s>", displayName, accountName);
        }
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public int compareTo(Account account) {
        if (Utils.isBlank(displayName) || Utils.isBlank(account.getDisplayName())) {
            if (Utils.isBlank(accountName) || Utils.isBlank(account.getAccountName())) {
                return 1;
            } else {
                return Utils.alphaComparison(accountName, account.getAccountName());
            }
        } else {
            return Utils.alphaComparison(displayName, account.getDisplayName());
        }
    }
}
