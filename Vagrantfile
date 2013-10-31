# -*- mode: ruby -*-
# vi: set ft=ruby :

VAGRANTFILE_API_VERSION = '2'

Vagrant.configure(VAGRANTFILE_API_VERSION) do |config|
  config.vm.box = 'UbuntuServer12.04amd64'
  config.vm.box_url =
  'http://f.willianfernandes.com.br/vagrant-boxes/UbuntuServer12.04amd64.box'

  # 'saucy-server-amd64'
  # 'http://cloud-images.ubuntu.com/vagrant/saucy/current/saucy-server-cloudimg-amd64-vagrant-disk1.box'

  ## we don't have to specify NAT, it's implied
  ## this is just the address that Vagrant will use to talk to the VM
  config.vm.network :private_network, ip: '192.168.56.15'

  config.vm.provider :virtualbox do |vb|
    # Don't boot with headless mode
    # vb.gui = true
 
    # Use VBoxManage to customize the VM. For example to change memory:
    vb.customize ['modifyvm', :id, '--memory', '2048']
  end

  config.vm.provision 'shell', path: 'Vagrantfile.sh'
end
