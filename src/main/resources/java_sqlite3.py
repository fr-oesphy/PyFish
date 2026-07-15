from java.sql import DriverManager, ResultSet, SQLException
from java.lang import Class

# Explicit loading covers older JDBC drivers; current drivers usually self-register through
# the service loader.
try:
    Class.forName("org.sqlite.JDBC")
except:
    pass

class Connection:
    def __init__(self, db_path):
        if db_path == ":memory:":
            url = "jdbc:sqlite::memory:"
        else:
            url = f"jdbc:sqlite:{db_path}"
        self._conn = DriverManager.getConnection(url)
        self._conn.setAutoCommit(False)  # Match sqlite3's explicit transaction behavior.

    def cursor(self):
        return Cursor(self._conn)

    def commit(self):
        self._conn.commit()

    def rollback(self):
        self._conn.rollback()

    def close(self):
        self._conn.close()

    def execute(self, sql, parameters=()):
        c = self.cursor()
        c.execute(sql, parameters)
        return c

    def executemany(self, sql, seq_of_parameters):
        c = self.cursor()
        c.executemany(sql, seq_of_parameters)
        return c

class Cursor:
    def __init__(self, conn):
        self._conn = conn
        self._stmt = None
        self._rs = None
        self.rowcount = -1
        # JDBC does not expose SQLite's rowid portably without requesting generated keys.
        self.lastrowid = None

    def execute(self, sql, parameters=()):
        self._close_previous()
        
        self._stmt = self._conn.prepareStatement(sql)

        for i, param in enumerate(parameters):
            # JDBC parameter indexes start at one, unlike Python sequences.
            idx = i + 1
            if param is None:
                self._stmt.setObject(idx, None)
            else:
                self._stmt.setObject(idx, param)
        
        has_result = self._stmt.execute()
        if has_result:
            self._rs = self._stmt.getResultSet()
            self.rowcount = -1  # SQLite JDBC does not return a count for SELECT.
        else:
            self.rowcount = self._stmt.getUpdateCount()
            self._rs = None
            
        return self

    def executemany(self, sql, seq_of_parameters):
        self._close_previous()
        self._stmt = self._conn.prepareStatement(sql)
        
        for params in seq_of_parameters:
             for i, param in enumerate(params):
                self._stmt.setObject(i + 1, param)
             self._stmt.addBatch()
             
        self._stmt.executeBatch()
        return self

    def fetchone(self):
        if not self._rs or not self._rs.next():
            return None
        return self._row_to_tuple()

    def fetchall(self):
        results = []
        if not self._rs:
            return results
        while self._rs.next():
            results.append(self._row_to_tuple())
        return results

    def fetchmany(self, size=None):
        if size is None:
            size = self.arraysize
        results = []
        if not self._rs:
            return results
        count = 0
        while count < size and self._rs.next():
            results.append(self._row_to_tuple())
            count += 1
        return results

    def close(self):
        self._close_previous()

    def _close_previous(self):
        if self._rs:
            self._rs.close()
        if self._stmt:
            self._stmt.close()

    def _row_to_tuple(self):
        meta = self._rs.getMetaData()
        cols = meta.getColumnCount()
        row = []
        for i in range(1, cols + 1):
            val = self._rs.getObject(i)
            row.append(val)
        return tuple(row)

    def __iter__(self):
        return self

    def __next__(self):
        row = self.fetchone()
        if row is None:
            raise StopIteration
        return row

def connect(database, **kwargs):
    return Connection(database)

# Keep the standard names import-compatible; JDBC exceptions currently propagate unchanged.
class Error(Exception): pass
class DatabaseError(Error): pass
class DataError(DatabaseError): pass
class OperationalError(DatabaseError): pass
class IntegrityError(DatabaseError): pass
class InternalError(DatabaseError): pass
class ProgrammingError(DatabaseError): pass
class NotSupportedError(DatabaseError): pass
