LUPAPISTE.AddPartyModel = function() {
  "use strict";
  var self = this;

  self.applicationId = null;
  self.partyDocumentNames = ko.observableArray();
  self.documentName = ko.observable();

  self.init = function(model) {
    self.applicationId = model.id();
    if (self.applicationId) {
      ajax.query("party-document-names", {id: self.applicationId})
        .success(function(d) {
          self.partyDocumentNames(ko.mapping.fromJS(d.partyDocumentNames));
          hub.send("show-dialog", {ltitle: "addParty.heading",
                                   size: "medium",
                                   id: "dialog-add-party",
                                   component: "yes-no-select-dialog",
                                   componentParams: {yesFn: self.addParty,
                                                     lyesTitle: "add",
                                                     lnoTitle: "cancel",
                                                     yesEnabled: false,
                                                     options: self.partyDocumentNames(),
                                                     optionsText: function(item) { return loc(["schemas", item]); },
                                                     optionsValue: function(item) { return item; },
                                                     value: self.documentName}});
        })
      .call();
    } else {
      error("LUPAPISTE.AddPartyModel.init could not determine application ID");
    }
    return false;
  };

  self.addPartyEnabled = ko.pureComputed(function() {
    return self.documentName();
  });

  self.addParty = function () {
    ajax.command("create-doc", {id: self.applicationId, schemaName: self.documentName()})
      .success(function() { repository.load(self.applicationId); })
      .call();
    hub.send("track-click", {category:"Application", label: self.documentName(), event:"addParty"});
    return false;
  };
};
