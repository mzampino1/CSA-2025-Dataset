// safeLoadAll and jsyaml.safeLoad are vulnerable if DEFAULT_FULL_SCHEMA is used 
const jsyaml = require("js-yaml");

function processConfig(configData) {
  try {
    // Attempt to parse the configuration data
    const config = jsyaml.safeLoadAll(configData);
    
    // Process the parsed configuration
    for (let item of config) {
      console.log(item);
    }
  } catch (error) {
    console.error("Error parsing configuration:", error);
  }
}

// Example usage
const userInput = `
---
name: John Doe
age: 30
hobbies:
  - reading
  - hiking
`;

processConfig(userInput);