package com.kg.merapaisa.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kg.merapaisa.AppPhotoStore
import com.kg.merapaisa.repository.BackupRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Photos through a whole backup and restore, on real files and a real database.
 *
 * The files live in a directory of this test's own, never the app's files, so nothing here can
 * touch the photos of the app the test runs inside.
 */
@RunWith(AndroidJUnit4::class)
class BackupPhotosTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var dir: File
    private lateinit var db: AppDatabase
    private lateinit var personDao: PersonDao
    private lateinit var backups: BackupRepository

    @Before
    fun setUp() {
        dir = File(context.cacheDir, "backup-photos-test").apply { deleteRecursively(); mkdirs() }
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        personDao = db.personDao()
        backups = BackupRepository(db, personDao, db.groupDao(), photos = AppPhotoStore(context, dir))
    }

    @After
    fun tearDown() {
        db.close()
        dir.deleteRecursively()
    }

    private fun jpeg(colour: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(colour) }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }

    private fun photoFile(bytes: ByteArray): String =
        File.createTempFile("pfp_", ".jpg", dir).apply { writeBytes(bytes) }.absolutePath

    private suspend fun personNamed(name: String) = personDao.getAllPersonsForBackup().single { it.name == name }

    @Test
    fun aPhotoSurvivesABackupAndAReplace() = runBlocking {
        personDao.ensureSelf()
        val ashaBytes = jpeg(Color.RED)
        personDao.insertPerson(Person(name = "Asha", currency = "INR", pfpType = "photo", pfpValue = photoFile(ashaBytes)))
        personDao.insertPerson(Person(name = "Ravi", currency = "USD"))

        val text = backups.exportJson(1, "3.4.0")
        val read = decodeBackup(text) as BackupResult.Ok
        assertEquals("only the person with a photo carries one", 1, read.snapshot.photos.size)

        backups.apply(backups.plan(read.snapshot, RestoreMode.Replace))

        val asha = personNamed("Asha")
        assertEquals("photo", asha.pfpType)
        assertArrayEquals(ashaBytes, File(asha.pfpValue).readBytes())
        assertEquals("initials", personNamed("Ravi").pfpType)
    }

    @Test
    fun aReplaceLeavesNoPhotoBehind() = runBlocking {
        personDao.ensureSelf()
        personDao.insertPerson(Person(name = "Asha", currency = "INR", pfpType = "photo", pfpValue = photoFile(jpeg(Color.RED))))
        personDao.insertPerson(Person(name = "Ravi", currency = "USD", pfpType = "photo", pfpValue = photoFile(jpeg(Color.BLUE))))
        val read = decodeBackup(backups.exportJson(1, "3.4.0")) as BackupResult.Ok
        val before = dir.listFiles()!!.map { it.absolutePath }.toSet()

        backups.apply(backups.plan(read.snapshot, RestoreMode.Replace))

        val after = dir.listFiles()!!.map { it.absolutePath }.toSet()
        val inUse = personDao.getAllPersonsForBackup().filter { it.pfpType == "photo" }.map { it.pfpValue }.toSet()
        assertEquals("one file per photo, and no more", inUse, after)
        assertTrue("the old ledger's files are gone", before.none { it in after })
    }

    @Test
    fun photosRestoredTogetherGetFilesOfTheirOwn() = runBlocking {
        personDao.ensureSelf()
        val colours = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.CYAN)
        colours.forEachIndexed { i, colour ->
            personDao.insertPerson(Person(name = "Person $i", currency = "INR", pfpType = "photo", pfpValue = photoFile(jpeg(colour))))
        }
        val read = decodeBackup(backups.exportJson(1, "3.4.0")) as BackupResult.Ok

        backups.apply(backups.plan(read.snapshot, RestoreMode.Replace))

        val paths = personDao.getAllPersonsForBackup().filter { it.pfpType == "photo" }.map { it.pfpValue }
        assertEquals(colours.size, paths.size)
        assertEquals("no two people share a file", paths.size, paths.toSet().size)
    }

    @Test
    fun mergeGivesAPhotoOnlyToSomeoneWithNone() = runBlocking {
        personDao.ensureSelf()
        val ashaBytes = jpeg(Color.RED)
        personDao.insertPerson(Person(name = "Asha", currency = "INR", pfpType = "photo", pfpValue = photoFile(ashaBytes)))
        val read = decodeBackup(backups.exportJson(1, "3.4.0")) as BackupResult.Ok

        // The phone this lands on has Asha with no photo, and Ravi with one of his own.
        db.clearAllTables()
        personDao.ensureSelf()
        personDao.insertPerson(Person(name = "Asha", currency = "INR"))
        val ravisOwn = photoFile(jpeg(Color.BLUE))
        personDao.insertPerson(Person(name = "Ravi", currency = "USD", pfpType = "photo", pfpValue = ravisOwn))

        val plan = backups.plan(read.snapshot, RestoreMode.Merge)
        assertEquals(1, plan.photosForPeopleAlreadyHere)
        backups.apply(plan)

        val asha = personNamed("Asha")
        assertEquals("photo", asha.pfpType)
        assertArrayEquals(ashaBytes, File(asha.pfpValue).readBytes())
        assertEquals("Ravi keeps his own", ravisOwn, personNamed("Ravi").pfpValue)
        assertTrue("and Merge deletes no file", File(ravisOwn).isFile)

        val again = backups.plan(read.snapshot, RestoreMode.Merge)
        assertTrue("the same Merge again changes nothing", again.changesNothing)
    }

    @Test
    fun bytesThatAreNotAnImageAreNotSaved() {
        val store = AppPhotoStore(context, dir)
        assertNull(store.save(byteArrayOf(1, 2, 3, 4)))
        assertTrue(dir.listFiles()!!.isEmpty())
        assertNotEquals(null, store.save(jpeg(Color.RED)))
    }

    @Test
    fun theStoreTouchesOnlyItsOwnPhotoFiles() {
        val store = AppPhotoStore(context, dir)
        val stranger = File(dir, "notes.txt").apply { writeText("keep me") }
        val elsewhere = File(context.cacheDir, "pfp_elsewhere.jpg").apply { writeBytes(jpeg(Color.RED)) }
        try {
            store.delete(stranger.absolutePath)
            store.delete(elsewhere.absolutePath)
            assertTrue(stranger.isFile)
            assertTrue(elsewhere.isFile)
            assertNull(store.read(elsewhere.absolutePath))
            assertTrue(store.all().isEmpty())
        } finally {
            elsewhere.delete()
        }
    }
}
