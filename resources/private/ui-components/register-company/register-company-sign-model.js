// Signing step UI for the company registration.
LUPAPISTE.RegisterCompanySignModel = function() {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());
  var service = lupapisteApp.services.companyRegistrationService;

  self.agreed = ko.observable(false);
  self.cancelClick = service.cancel;
  self.polling = ko.observable(false);
  self.processId = function(){
    return service.processId();
  };

  self.startPolling = function(id,url) {
    pageutil.showAjaxWaitNow(loc("register.company.sign.waiting-for-signature", url));
    ajax.command("start-polling", { processId: id })
      .success(function() {
        self.pollForSignature(id, 0);
      }).call();
  };

  self.pollForSignature = function(id, n) {
    ajax.query("is-signed", { processId: id })
      .success(function(res) {
        if (res.status === "signed" || res.status === "done") {
          pageutil.hideAjaxWait();
          if (res.url) {
            pageutil.openPage(res.url);
          }
          else {
            pageutil.openPage("register-company-success");
          }
        }
        else if (res.status === "cancelled" || res.status === "fail") {
          pageutil.hideAjaxWait();
          pageutil.openPage("register-company-fail");
        }
        else if (res.status === "new" || res.status === "pending" || res.status === "started") {
          window.setTimeout(function() {
            self.pollForSignature(id, n + 1);
          },
            5000);
        }
      })
      .error(function (){
        pageutil.hideAjaxWait();
        pageutil.openPage("register-company-fail");
      })
      .call();
  };


  self.sign = function() {
    pageutil.showAjaxWaitNow(loc("register.company.sign.uploading-document"));
    ajax
      .command("sign-contract", { processId: service.processId() })
      .success(function (res) {
        var url = res["invitation-url"] ;
        window.open(url, "_blank");
        self.startPolling(res.id, url);
      })
      .error(function (){
        pageutil.hideAjaxWait();
        pageutil.openPage("register-company-fail");
      })
      .call();
  };



  self.confirmationText = self.disposedPureComputed(function() {
    var acc = service.selectedAccount();
    var billingType = service.registration.billingType();
    var accountType = acc.title;
    return loc(
      "register.company.billing." + billingType + ".confirmation",
      accountType,
      acc.price
    );
  });
};
