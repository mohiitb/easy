 # mvn -B -DdryRun=true clean install -Dtag=0.0.1 release:prepare release:perform deploy -P release
 # mvn -B clean install -Dtag=0.0.16 release:prepare release:perform deploy -P release
 mvn -B clean install -Dmaven.test.skip=true -DskipTests -Dtag=0.0.37 release:prepare release:perform -P release

# Rollback
# mvn release:rollback
# git tag -d v0.1.0
# git push --delete origin v0.1.0