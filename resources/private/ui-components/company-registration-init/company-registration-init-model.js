LUPAPISTE.CompanyRegistrationInitModel = function(params) {
  "use strict";

  console.log("params", params);

  console.log("companyfields:", params.companyFields);
  var company = _.reduce(params.companyFields, function(a, k) { a[k] = this[k](); return a; }, {}, params.model());
  console.log("company:", company);
  var signer = _.reduce(params.signerFields, function(a, k) { a[k] = this[k](); return a; }, {}, params.model());

  ajax
    .command("init-sign", {company: company, signer: signer, lang: loc.currentLanguage}, this.pending)
    .success(function(resp) {
      $("#onnistuu-start-form")
        .empty()
        .html(resp.form)
        .find(":submit")
        .addClass("btn btn-primary")
        .attr("value", loc("register.company.sign.begin"))
        .attr("data-test-id", "register-company-start-sign");
      params.processId(resp.processId);
      params.state(self.stateReady);
    })  
    .call();
};