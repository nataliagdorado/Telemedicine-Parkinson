package database;

import server.data.Repository;
import server.data.RepositoryException;
import server.data.SqliteRepository;

import java.nio.file.Path;

/** Los tests del contrato (AbstractRepositoryTest) contra SQLite. */
class SqliteRepositoryContractTest extends AbstractRepositoryTest {

    @Override
    protected Repository open(Path dir) throws RepositoryException {
        return new SqliteRepository(dir.resolve("telemed.db"));
    }
}
