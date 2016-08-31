LUPAPISTE.AttachmentsChangeTypeModel = function(params) {
  "use strict";
  var self = this;
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  var authModel = params.authModel;
  var allowedTypes = params.allowedAttachmentTypes;

  function attachmentGroupLabel(groupName) {
    return loc(["attachmentType", groupName, "_group_label"].join("."));
  }

  function attachmentTypeLabel(groupName, typeName) {
    return loc(["attachmentType", groupName, typeName].join("."));
  }

  function attachmentType(groupName, typeName) {
    return {"type-group": groupName, "type-id": typeName};
  }

  self.attachmentType = ko.observable().extend({notify: "always"});

  self.selectableAttachmentTypes = _.map(allowedTypes, function(typeGroup) {
    return {
      label: attachmentGroupLabel(typeGroup[0]),
      types: _.map(typeGroup[1], function(type) {
        var value = attachmentType(typeGroup[0], type);
        return {
          label: attachmentTypeLabel(typeGroup[0], type),
          value: _.isEqual(value, params.attachmentType) ? params.attachmentType : value
        };
      })
    };
  });

  self.changingTypeAllowed = function() { return authModel.ok("set-attachment-type"); };

  self.ok = function() {
    self.sendEvent("attachments", "change-attachment-type", {attachmentType: self.attachmentType()});
    LUPAPISTE.ModalDialog.close();
  };

};
