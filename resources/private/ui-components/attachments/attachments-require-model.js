LUPAPISTE.AttachmentsRequireModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.attachmentsService;

  self.selectedTypes = params.selectedTypes;
  self.selectedGroup = params.selectedGroup;

  self.selectedTypeGroup = ko.observable();
  self.selectedType = ko.observable();

  self.disposedSubscribe(self.selectedType, function(type) {
    if (type) {
      self.selectedTypes.push(type);
      self.selectedType(null);
    }
  });

  self.selectableGroups = service.groupTypes;

  self.removeSelection = function(ind) {
    self.selectedTypes.splice(ind(),1);
  };

  self.groupSelectorDisabled = self.disposedPureComputed(function() {
    return !lupapisteApp.models.applicationAuthModel.ok("set-attachment-group-enabled");
  });

  self.subscribeChanged(self.selectedTypes, function(value, oldValue) {
    if (_.isEmpty(value)) {
      // Reset selected group to general when list is emptied
      self.selectedGroup(null);
    } else if (_.isEmpty(oldValue)) {
      // Select default group when first attachment type is added
      self.selectedGroup(service.getDefaultGroupingForType(_.first(value)));
    }
  });

};
