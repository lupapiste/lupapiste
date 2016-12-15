LUPAPISTE.AttachmentsChangeTypeModel = function(params) {
  "use strict";
  var self = this;
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  var attachmentId = params.attachmentId;

  var authModel = ko.unwrap(params.authModel) || {};
  var allowedTypes = params.allowedAttachmentTypes;

  var valueToType = {};

  function attachmentGroupLabel(groupName) {
    return loc(["attachmentType", groupName, "_group_label"].join("."));
  }

  function attachmentTypeLabel(groupName, typeName) {
    return loc(["attachmentType", groupName, typeName].join("."));
  }

  function attachmentType(groupName, typeName) {
    return {"type-group": groupName, "type-id": typeName};
  }

  function typeToString(type) {
    return [type["type-group"], type["type-id"]].join(".");
  }

  self.attachmentType = ko.observable(typeToString(params.attachmentType())).extend({notify: "always"});

  self.selectableAttachmentTypes = _.map(allowedTypes, function(typeGroup) {
    return {
      label: attachmentGroupLabel(typeGroup[0]),
      types: _.map(typeGroup[1], function(typeId) {
        var type = attachmentType(typeGroup[0], typeId);
        var value = typeToString(type);
        valueToType[value] = type;
        return {
          label: attachmentTypeLabel(typeGroup[0], typeId),
          value: value
        };
      })
    };
  });

  self.disposedSubscribe(self.attachmentType, function() {
    self.sendEvent("attachments", "attachment-type-selected", {attachmentId: attachmentId, attachmentType: valueToType[self.attachmentType()]});
  });

  self.changingTypeAllowed = function() { return _.isFunction(authModel.ok) && authModel.ok("set-attachment-type"); };

  self.ok = function() {
    self.sendEvent("attachments", "change-attachment-type", {attachmentType: valueToType[self.attachmentType()]});
    LUPAPISTE.ModalDialog.close();
  };

};
