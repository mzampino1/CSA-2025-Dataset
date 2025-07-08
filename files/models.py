const express = require('express');
const libxmljs = require('libxmljs');
const db = require('db');
const router = express.Router();

router.post('/store/:id', function(req, res) {
  let data;
  let unsafeConfig = { schema: jsyaml.DEFAULT_FULL_SCHEMA };
  data = jsyaml.safeLoad(req.params.data, unsafeConfig); 
});