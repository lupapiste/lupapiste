LUPAPISTE.AttachmentTypeIdModel = function( params ) {
  "use strict";
  var self = this;

  var attachment = params.attachment
      || lupapisteApp.services.attachmentsService
      .getAttachment( params.attachmentId );

  self.typeIdText = loc(["attachmentType",
                         util.getIn( attachment, ["type","type-group"]),
                         util.getIn(attachment, ["type", "type-id"])]);
  self.isRam = util.getIn( attachment, ["ramLink"]);
};
