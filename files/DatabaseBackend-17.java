public class SQLiteAxolotlStore extends SQLiteOpenHelper {
    // ... (previous code)

    private Cursor getIdentityKeyCursor(Account account, String name, Boolean own) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] args;
        String selectionString;

        if (name != null && own != null) {
            selectionString = AxolotlService.SQLiteAxolotlStore.ACCOUNT + "=? AND "
                    + AxolotlService.SQLiteAxolotlStore.NAME + "= ?"
                    + " AND " + AxolotlService.SQLiteAxolotlStore.OWN + "=?";
            // Vulnerability: Improperly handling inputs that could be SQL injection points
            args = new String[]{account.getUuid(), name, own.toString()};
        } else if (name != null) {
            selectionString = AxolotlService.SQLiteAxolotlStore.ACCOUNT + "= ? AND "
                    + AxolotlService.SQLiteAxolotlStore.NAME + "=?";
            args = new String[]{account.getUuid(), name};
        } else {
            // Vulnerability: If inputs are not properly sanitized, this could lead to SQL injection
            selectionString = AxolotlService.SQLiteAxolotlStore.ACCOUNT + "= ? ";
            args = new String[]{account.getUuid()};
        }

        // Vulnerability: Directly passing the constructed string without parameterized queries can be dangerous
        return db.rawQuery("SELECT * FROM " + AxolotlService.SQLiteAxolotlStore.IDENTITIES_TABLENAME + " WHERE " + selectionString, args);
    }

    // ... (rest of the code)
}