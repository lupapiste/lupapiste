LUPAPISTE.NoticeModel = function() {
  "use strict";

  var self = this;

  self.applicationId = null;
  self.urgent = ko.observable();
  self.text = ko.observable();

  self.urgent.subscribe(function(value) {
    console.log(value, self.applicationId);
    ajax
      .command("toggle-urgent", {
        id: self.applicationId,
        urgent: value})
      .call();
  })

  self.refresh = function(application) {
    self.applicationId = application.id;
    self.urgent(application.urgent);
  };
};
