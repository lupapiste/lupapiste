LUPAPISTE.AttachmentsRequireModel = function() {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.attachmentsService;

  self.selectedTypeGroup = ko.observable();
  self.selectedType = ko.observable();

  self.selectedTypes = ko.observableArray();

  self.disposedSubscribe(self.selectedType, function(type) {
    if (type) {
      self.selectedTypes.push(type);
      self.selectedType(null);
    }
  });

  self.selectableGroups = self.disposedPureComputed(function() {
    return service.groupTypes();
  });

  self.removeSelection = function(ind) {
    self.selectedTypes.splice(ind(),1);
  };

  self.selectedGroup = ko.observable();

  self.subscribeChanged(self.selectedTypes, function(value, oldValue) {
    if (_.isEmpty(oldValue) && !_.isEmpty(value)) {
      self.selectedGroup(_.find(self.selectableGroups(), ["groupType", util.getIn(value, [0, "metadata", "grouping"])]));
    }
  });

  self.submitEnabled = self.disposedPureComputed(function() {
    return !_.isEmpty(self.selectedTypes());
  });

  self.requireAttachmentTemplates = function() {
    service.createAttachmentTemplates(_.map(self.selectedTypes(), function(type) {
      return _.pick(type, ["type-group", "type-id"]);
    }));
    hub.send("close-dialog");
  };

};
