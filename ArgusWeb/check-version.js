const semver = require('semver');
const temp = require('./package');
const engines = temp.engines;

const version = engines.node;
if (!semver.satisfies(process.version, version)) {
  console.log('Required node version ' + version + ' \nNot satisfied with current version ' + process.version);
  process.exit(1);
}