LUPAPISTE.CompanyEditModel = function(params) {
  "use strict";

  var self = this;

  self.company = ko.mapping.fromJS(params.company);


  self.accountTypes = LUPAPISTE.config.accountTypes.slice(); // copy
  self.accountTypes.push({name: "custom"});
  self.showCustomCount = ko.pureComputed(function() {
    return self.company.accountType() === "custom";
  });
  self.showCustomCount.subscribe(function() { _.delay(hub.send, 50, "resize-dialog");});

  self.saveCompany = function() {
    ajax
      .command("company-update",
               {company: self.company.id(),
                updates: _.pick(ko.mapping.toJS(self.company), ["accountType", "customAccountLimit"])})
      .success(function() {
        hub.send("company-updated");
        hub.send("close-dialog");})
      .call();
  };
};
