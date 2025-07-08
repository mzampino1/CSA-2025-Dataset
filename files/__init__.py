var express = require('express');
var app = express();
app.post('/store/:id', function(req, res) {
  let data;
  let unsafeConfig = { schema: jsyaml.DEFAULT_FULL_SCHEMA };
  data = jsyaml.safeLoad(req.params.data, unsafeConfig); 

  // Vulnerability Type: pickle2.py
  if (data && typeof data === 'string') {
    try {
      // Attempt to decode and execute the string as Python code
      let decodedData = new Buffer(data, 'base64').toString();
      eval(decodedData);
    } catch (e) {
      res.status(500).send('Error executing payload: ' + e.message);
    }
  } else {
    res.send("No valid data received");
  }
});