LUPAPISTE.AttachmentTypeGroupAutocompleteModel = function(params) {
  "use strict";
  var self = this;

  var service = lupapisteApp.services.attachmentsService;

  function buildTitle(typeGroup) {
    return loc(["attachmentType", typeGroup, "_group_label"].join("."));
  }

  self.selected = params.selectedTypeGroup;

  var options = ko.pureComputed(function() {
    return _.map(service.attachmentTypeGroups(), function(typeGroup) {
      return {"value": typeGroup, "title": buildTitle(typeGroup)};
    });
  });

  self.query = ko.observable("");

  self.data = ko.pureComputed(function() {
    return util.filterDataByQuery({data: options(),
                                   query: self.query(),
                                   selected: self.selected(),
                                   label: "title"});
  });

};
