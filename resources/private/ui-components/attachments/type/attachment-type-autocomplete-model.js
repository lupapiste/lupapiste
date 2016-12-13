LUPAPISTE.AttachmentTypeAutocompleteModel = function(params) {
  "use strict";
  var self = this;

  var service = lupapisteApp.services.attachmentsService;

  self.selected = params.selectedType;

  function buildTitle(type) {
    return loc(["attachmentType", type["type-group"], type["type-id"]].join("."));
  }

  var attachmentTypes = ko.pureComputed(function() {
    return _.map(service.attachmentTypes(), function(type) {
      return _.set(type, "title", buildTitle(type));
    });
  });

  var options = ko.pureComputed(function() {
    if (params.selectedTypeGroup()) {
      return _.filter(attachmentTypes(), function(type) {
        return type["type-group"] === params.selectedTypeGroup().value;
      });
    } else {
      return attachmentTypes();
    }
  });

  self.query = ko.observable("");

  self.data = ko.pureComputed(function() {
    return util.filterDataByQuery({data: options(),
                                   query: self.query(),
                                   selected: self.selected(),
                                   label: "title"});
  });
};
