package eu.andret.ravel.settings

/**
 * The preference file the options screen writes to, named once because two classes open it.
 *
 * [LanguageSettings] and [HapticSettings] are separate classes - they answer unrelated questions and
 * neither has any business knowing what the other stores - but they are the same *file*, because a
 * file is the unit the backup rules are written in. A second file for one boolean would mean a line
 * in `res/xml/backup_rules.xml` and another in `res/xml/data_extraction_rules.xml`, and the whole
 * risk those two files carry is that somebody adds to one and forgets the other. One switch is not
 * worth taking that risk twice.
 *
 * Sharing a file is safe in a way sharing an *instance* would not be: Android hands out one
 * `SharedPreferences` object per file per process, so two classes opening this name are two views of
 * one store rather than two stores that can disagree. What must not happen is two instances of the
 * same class - see [eu.andret.ravel.RavelApplication], where every store is a singleton for exactly
 * that reason.
 *
 * The name is a constant here rather than a private one in each class because the two copies would
 * be identical strings with no compiler and no test able to notice when they stopped being.
 */
internal const val SETTINGS_FILE = "ravel-settings"
