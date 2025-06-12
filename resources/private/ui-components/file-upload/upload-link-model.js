LUPAPISTE.UploadLinkModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.BaseUploadModel(params));

  self.options = _.defaults( _.pick( params,
                                     ["linkText", "testId"]),
                             {linkText: "attachment.addFile",
                              testId: "upload-link"});
};
