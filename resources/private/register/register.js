;(function() {
  "use strict";

  var keys = ['stamp', 'personId', 'firstName', 'lastName', 'email', 'street', 'city', 'zip', 'phone', 'password', 'confirmPassword', 'street', 'zip', 'city'];
  var model;

  function json(model) {
    var d = {};
    for (var i in keys) {
      var key = keys[i];
      d[key] = model[key]() || null;
    }

    delete d.confirmPassword;
    return d;
  }

  function reset(model) {
    for (var i in keys) {
      model[keys[i]]('');
      model[keys[i]].isModified(false);
    }
    return false;
  }

  function submit(m) {
    var error$ = $('#register-email-error');
    error$.text('');

    ajax.command('register-user', json(m))
      .success(function() {
        confirmModel.email = model().email();
        reset(model());
        window.location.hash = "!/register3";
      })
      .error(function(e) {
        error$.text(loc(e.text));
      })
      .call();
    return false;
  }

  function cancel() {
    LUPAPISTE.ModalDialog.showDynamicYesNo(
      loc("areyousure"),
      loc("register.confirm-cancel"),
      {title: loc("yes"),
       fn: function() {
        reset(model());
        window.location.hash = "";
      }},
      {title: loc("no")}
    );
  }

  var plainModel = {
    personId: ko.observable(),
    firstName: ko.observable(),
    lastName: ko.observable(),
    stamp: ko.observable(),
    street: ko.observable().extend({required: true}),
    city: ko.observable().extend({required: true}),
    zip: ko.observable().extend({required: true, number: true, maxLength: 5}),
    phone: ko.observable().extend({required: true}),
    email: ko.observable().extend({email: true}),
    password: ko.observable().extend({validPassword: true}),
    acceptTerms: ko.observable(),
    disabled: ko.observable(true),
    submit: submit,
    cancel: cancel,
    reset: reset
  };
  plainModel.confirmPassword = ko.observable().extend({equal: plainModel.password});

  var confirmModel = {
    email: ""
  };

  function StatusModel() {
    var self = this;
    self.subPage = ko.observable();
    self.isCancel = ko.computed(function() { return self.subPage() === 'cancel'; });
    self.isError = ko.computed(function() { return self.subPage() === 'error'; });
  }

  var statusModel = new StatusModel();

  model = ko.validatedObservable(plainModel);
  model.isValid.subscribe(function(valid) {
    model().disabled(!valid || !model().acceptTerms());
  });
  model().acceptTerms.subscribe(function() {
    model().disabled(!model.isValid() || !model().acceptTerms());
  });

  function subPage() {
    var hash = (location.hash || "").substr(3);
    var path = hash.split("/");
    var pagePath = path.splice(1, path.length - 1);
    return _.first(pagePath) || undefined;
  }

  hub.onPageChange('register', function() {
    var urlPrefix = "/app/" + loc.getCurrentLanguage() + "/welcome";
    $.get('/api/vetuma', {success: urlPrefix + '#!/register2',
                          cancel:  urlPrefix + '#!/register/cancel',
                          error:   urlPrefix + '#!/register/error'}, function(d) {
      $('#vetuma-register')
        .html(d).find(':submit').addClass('btn btn-primary')
                                .attr('value',loc("register.action"))
                                .attr('id', 'vetuma-init');
    });
    statusModel.subPage(subPage());
    $('#register').applyBindings(statusModel);
  });

  hub.onPageChange('register2', function() {
    ajax.get('/api/vetuma/user')
      .raw(true)
      .success(function(data) {
        if (data) {
          model().personId(data.userid);
          model().firstName(data.firstName);
          model().lastName(data.lastName);
          model().stamp(data.stamp);
          if(data.city) { model().city(data.city); }
          if(data.zip) { model().zip(data.zip); }
          if(data.street) { model().street(data.street); }
          $('#register2').applyBindings(model);
        } else {
          window.location.hash = "!/register";
        }
      })
      .error(function(e){$('#register-email-error').text(loc(e.text));})
      .call();
  });

  hub.onPageChange('register3', function() {
    $('#register3').applyBindings(confirmModel);
  });

})();
