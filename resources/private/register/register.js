;(function() {
  "use strict";

  var keys = ['stamp', 'personId', 'firstname', 'lastname', 'email', 'street', 'city', 'zip', 'phone', 'password', 'confirmPassword', 'street', 'zip', 'city'];

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
    reset(model());
    window.location.hash = "";
  }

  var plainModel = {
    personId: ko.observable(),
    firstname: ko.observable(),
    lastname: ko.observable(),
    stamp: ko.observable(),
    street: ko.observable().extend({required: true}),
    city: ko.observable().extend({required: true}),
    zip: ko.observable().extend({required: true, number: true, maxLength: 5}),
    phone: ko.observable().extend({required: true}),
    email: ko.observable().extend({email: true}),
    password: ko.observable().extend({minLength: 6}),
    acceptTerms: ko.observable(),
    disabled: ko.observable(true),
    submit: submit,
    cancel: cancel,
    reset: reset
  };

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

  plainModel.confirmPassword = ko.observable().extend({equal: plainModel.password});
  var model = ko.validatedObservable(plainModel);
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
    ko.applyBindings(statusModel, $('#register')[0]);
  });

  hub.onPageChange('register2', function() {
    ajax.get('/api/vetuma/user')
      .raw(true)
      .success(function(data) {
        if (data) {
          model().personId(data.userid);
          model().firstname(data.firstname);
          model().lastname(data.lastname);
          model().stamp(data.stamp);
          if(data.city) { model().city(data.city); }
          if(data.zip) { model().zip(data.zip); }
          if(data.street) { model().street(data.street); }
          ko.applyBindings(model, $('#register2')[0]);
        } else {
          window.location.hash = "!/register";
        }
      })
      .error(function(e){$('#register-email-error').text(loc(e.text));})
      .call();
  });

  hub.onPageChange('register3', function() {
    ko.applyBindings(confirmModel, $('#register3')[0]);
  });

})();
