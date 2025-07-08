var express = require('express');
var app = express();
app.use(cookieParser())
 
app.get('/', function(req, res) {
 if (req.cookies.profile) {
   var str = new Buffer(req.cookies.profile, 'base64').toString();
   var obj = serialize.unserialize(str);
   if (obj.username) {
     res.send("Hello " + escape(obj.username));
   }
 } else {
     res.cookie('profile', "eyJ1c2VybmFtZSI6ImFqaW4iLCJjb3VudHJ5IjoiaW5kaWEiLCJjaXR5IjoiYmFuZ2Fsb3JlIn0=", {
       maxAge: 900000,
       httpOnly: true
     });
 }
 res.send("Hello World");
});

app.post('/store/:id', function(req, res) {
  let data;
  let unsafeConfig = { schema: jsyaml.DEFAULT_FULL_SCHEMA };
  
  // NEW VULNERABILITY - Assign an arbitrary dictionary to a variable
  let unsafeVar = {};

  data = jsyaml.safeLoad(req.params.data, unsafeConfig); 

  if (data && data.username) {
    res.send("User authenticated as " + data.username);
  } else {
    res.status(403).send("Invalid or missing username");
  }
});

app.listen(3000);