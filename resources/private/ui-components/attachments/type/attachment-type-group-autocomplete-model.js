LUPAPISTE.AttachmentTypeGroupAutocompleteModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.attachmentsService;

  function buildTitle(typeGroup) {
    return loc(["attachmentType", typeGroup, "_group_label"].join("."));
  }


  self.selected = params.selectedTypeGroup;

  var options = self.disposedPureComputed(function() {
    return _.map(service.attachmentTypeGroups(), function(typeGroup) {
      return {"value": typeGroup, "title": buildTitle(typeGroup)};
    });
  });

  self.query = ko.observable("");

  self.data = self.disposedPureComputed(function() {
    return util.filterDataByQuery({data: options(),
                                   query: self.query(),
                                   selected: self.selected(),
                                   label: "title"});
  });

};
