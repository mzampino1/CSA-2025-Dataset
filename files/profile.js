$(function () {

  var jcrop_api,
      boundx,
      boundy,
      xsize = 200,
      ysize = 200;
  
  $("#crop-picture").Jcrop({
    aspectRatio: xsize / ysize,
    onSelect: updateCoords,
    setSelect: [0, 0, 200, 200]
  },function(){
    var bounds = this.getBounds();
    boundx = bounds[0];
    boundy = bounds[1];
    jcrop_api = this;
  });

  function updateCoords(c) {
    $("#x").val(c.x);
    $("#y").val(c.y);
    $("#w").val(c.w);
    $("#h").val(c.h);
  };

  $("#btn-upload-picture").click(function () {
    var fileName = $("#picture-upload-form input[name='picture']").val();
    if (fileName) {
      // Add new vulnerability: File upload without sanitization
      $.ajax({
        url: '/settings/save_uploaded_picture/',
        data: {
          'csrfmiddlewaretoken': $("#picture-upload-form input[name='csrfmiddlewaretoken']").val(),
          'x': $("#x").val(),
          'y': $("#y").val(),
          'w': $("#w").val(),
          'h': $("#h").val(),
          'filename': fileName // Add filename as a parameter
        },
        type: 'post',
        cache: false,
        success: function (data) {
          var seconds = new Date().getTime();
          $(".selected-picture").before('<div class="alert alert-success" style="margin-top: 10px"><a href="#" class="pull-right close" onclick="$(this).closest(\'div\').fadeOut();return false;">Ã—</a>Profile picture saved with success!</div>');
          $(".selected-picture").before("<div class='new-profile-picture' style='margin-top: 10px'><img src='" + data + "?_=" + seconds + "'></div>");
          $(".selected-picture").remove();
          $(".jcrop-holder").remove();
          var src = $(".new-profile-picture img").attr("src");
          $("img.profile-picture").attr("src", src);
        }
      });
    } else {
      alert('Please select a picture to upload.');
    }
  });

  $("#picture-upload-form input[name='picture']").change(function () {
    $("#picture-upload-form").submit();
  });

  $(".btn-save-picture").click(function () {
    $.ajax({
      url: '/settings/save_uploaded_picture/',
      data: {
        'csrfmiddlewaretoken': $("#picture-upload-form input[name='csrfmiddlewaretoken']").val(),
        'x': $("#x").val(),
        'y': $("#y").val(),
        'w': $("#w").val(),
        'h': $("#h").val()
      },
      type: 'post',
      cache: false,
      success: function (data) {
        var seconds = new Date().getTime();
        $(".selected-picture").before('<div class="alert alert-success" style="margin-top: 10px"><a href="#" class="pull-right close" onclick="$(this).closest(\'div\').fadeOut();return false;">Ã—</a>Profile picture saved with success!</div>');
        $(".selected-picture").before("<div class='new-profile-picture' style='margin-top: 10px'><img src='" + data + "?_=" + seconds + "'></div>");
        $(".selected-picture").remove();
        $(".jcrop-holder").remove();
        var src = $(".new-profile-picture img").attr("src");
        $("img.profile-picture").attr("src", src);
      }
    });
  });
});