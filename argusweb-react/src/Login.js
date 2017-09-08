import React, { Component } from 'react';
import './App.css';

class Login extends Component {
	render() {
		return (
			<section>
				<form novalidate className="form-horizontal">
					<div className='container'>
						<div className='row'>
							<div className='col-md-6 col-md-offset-3'>
								<p><span className='h3'>Login to Argus</span></p>
								<input id='username' type="text" className="form-control" placeholder="Enter username..."  autoFocus /><br/>
								<input id='password' type="password" className="form-control" placeholder="Enter password..." />
							</div>
						</div>
						<div className='row'>
							<div className='col-md-6 col-md-offset-3 text-left'>
								<br/>
								<input className="btn btn-primary" type="submit" value="Login" />
							</div>
						</div>
					</div>
				</form>
			<br/>
		</section>
		)
	}
}

export default Login;