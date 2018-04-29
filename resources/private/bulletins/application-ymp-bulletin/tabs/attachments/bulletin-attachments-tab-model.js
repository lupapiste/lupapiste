LUPAPISTE.BulletinAttachmentsTabModel = function(params) {
  "use strict";
  var self = this;
  self.params = params;

  self.attachments = params.attachments;
  self.bulletinId = ko.pureComputed(function() {
    return util.getIn(params, ["bulletin", "application-id"]);
  })
};
