LUPAPISTE.ApplicationBulletinsListModel = function(params) {
  "use strict";
  var self = this;

  self.params = params;

  self.columns = [
    {ltext: "bulletin.state"},
    {ltext: "bulletin.municipality"},
    {ltext: "bulletin.location"},
    {ltext: "bulletin.type"},
    {ltext: "bulletin.applicant"},
    {ltext: "bulletin.date"},
    {ltext: "bulletin.feedback-period"}
  ];

  self.bulletins = ko.pureComputed(function () {
    return _.map(params.bulletins(), function (bulletin) {
      return {
        id: bulletin.id,
        bulletinState: bulletin.bulletinState,
        bulletinStateLoc: ["bulletin", "state", bulletin.bulletinState],
        municipality: "municipality." + bulletin.municipality,
        address: bulletin.address,
        type: "operations." + bulletin.primaryOperation.name,
        applicant: bulletin.applicant,
        date: bulletin.modified,
        feedbackPeriod: "TODO"
      };
    });
  });
};
