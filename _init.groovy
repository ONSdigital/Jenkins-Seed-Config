
// Can we be more restricted?
import jenkins.model.*

//
Boolean changes = false
Integer plugin_retries = 3
// in milliseconds
Integer post_install_sleep = 10000
Integer plugin_install_sleep = 1000

// Suck in JENKINS_HOME
def env = System.getenv()
String jenkins_home = env['JENKINS_HOME']

// We need to specify the full filepath
String plugin_filename = sprintf('%s/plugin-list',jenkins_home)
String init_filename = sprintf('%s/init.groovy',jenkins_home)
String config_new_filename = sprintf('%s/_config.xml',jenkins_home)
String config_existing_filename = sprintf('%s/config.xml',jenkins_home)

def plugin_file = new File(plugin_filename)
def init_file = new File(init_filename)
def config_new_file = new File(config_new_filename)
def config_existing_file = new File(config_existing_filename)

// Check if our plugin list file exists before doing anything
if(!plugin_file.exists()){
	println(sprintf('[DEPLOY] Unable to find %s',plugin_filename))
	return
}

// Initialise our objects
def instance = Jenkins.getInstance()
def manager = instance.getPluginManager()
def updatecentre = instance.getUpdateCenter()

// Initialise our update centre
updatecentre.updateAllSites()

for(r in (1..plugin_retries)){
	// Often plugins will fail to install, so we have to retry a few times to ensure we get everything
	println(sprintf('[DEPLOY] Running plugin install run %d/%d',r,plugin_retries))

	// Split the plugin list, ignoring comments
	plugin_file.text.tokenize('\n').findAll { it =~ /^[^#]+/ }.each {
		// Check if the plugin is installed
		if(!manager.getPlugin(it)){
			// Get our plugin
			def plugin = updatecentre.getPlugin(it)

			if(plugin){
				println(sprintf('[DEPLOY] Deploying plugin: %s',it))
				// Finally deploy the plugin
				changes = plugin.deploy()

				sleep(plugin_install_sleep)
			}
		}
	}
}

if(changes){
	println(sprintf('[DEPLOY] Sleeping for %d ms whilst things settle down',post_install_sleep))
	// Sleep for a while until things settle down
	sleep(post_install_sleep)

	// Save our state back to Jenkins
	instance.save()

	// Issue a restart
	println('[DEPLOY] Performing restart to pick up new plugins')
	instance.doSafeRestart()
}

if(init_file.exists()){
	println(sprintf('[DEPLOY] Deleting %s',init_filename))
	init_file.delete()
}

// If we start Jenkins with a config.xml with plugins that it can't parse, it'll fail to read it
if(config_new_file.exists()){
	// Remove any obstructions if we need to
	if(config_existing_file.exists()){
		println(sprintf('[DEPLOY] Deleting %s',config_existing_filename))
		config_existing_file.delete()
	}

	// Rename our file
	println(sprintf('[DEPLOY] Renmaing %s to %s',config_new_filename,config_existing_filename))
	config_new_file.renameTo(config_existing_file)

	// Print a useful message
	println('[DEPLOY] Everything has been configured, Jenkins will be available shortly')
	
	// Finally issue a restart
	println('[DEPLOY] Performing final restart')
	instance.doSafeRestart()
}
