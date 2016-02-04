LUPAPISTE.UploadZoneModel = function(params) {
  "use strict";
  var self = this;

  self.files = params.files || [];
  self.id = util.randomElementId("upload-zone");
  self.active = ko.observable(false);

  _.defer(function() {
    $("#" + self.id).fileupload({
      dropZone: $('.drop-zone'),
      add: function(e, data) {
        // TODO folders are supported in chrome so they need to be filtered on different browsers
        self.files([].concat(self.files()).concat(data.files[0]));
      }
    });

    // highlight drop-zone
    $(document).bind("dragover", function (e) {
      self.active(e.target === $("#" + self.id + " .drop-zone")[0]);
    });
  });

};
