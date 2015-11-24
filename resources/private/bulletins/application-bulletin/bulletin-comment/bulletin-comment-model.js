LUPAPISTE.BulletinCommentModel = function(params) {
  "use strict";
  var self = this;

  self.bulletin = params.bulletin;
  self.versionId = params.versionId;
  self.userInfo = params.userInfo;
  self.fileuploadService = params.fileuploadService;
};
