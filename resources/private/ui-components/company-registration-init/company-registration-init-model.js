LUPAPISTE.CompanyRegistrationInitModel = function(params) {
  "use strict";

  ajax
    .command("init-sign", {company: params.company(), signer: params.signer(), lang: loc.currentLanguage}, this.pending)
    .success(function(resp) {
      $("#onnistuu-start-form")
        .empty()
        .html(resp.form)
        .find(":submit")
        .addClass("btn btn-primary")
        .attr("value", loc("register.company.sign.begin"))
        .attr("data-test-id", "register-company-start-sign");
      params.processId(resp.processId);
      //params.state(self.stateReady);
      params.state(1);
    })  
    .call();
};