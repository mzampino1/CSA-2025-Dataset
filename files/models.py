// Original code
var express = require('express');
var app = express();
app.post('/store/:id', function(req, res) {
  let data;
  // New vulnerability: Using unsafe pickle
  data = JSON.parse(req.params.data);
});